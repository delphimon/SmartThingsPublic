/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  MODIFIED 2/14/17 plenTpak to work with Monoprice Z-Wave Plus Plug-In Power Monitor P/N 15903
 *  MODIFIED 12/16/2019 by delphimon to work with hub update for encapsulated commands
 */
metadata {
	definition (name: "Monoprice Z-Wave Plus Plug-In Power Monitor P/N 15903", namespace: "plentpak", author: "plenTpak") {
		capability "Energy Meter"
		capability "Power Meter"
		capability "Configuration"

        capability "Switch"
		capability "Refresh"
		capability "Polling"
		capability "Actuator"
		capability "Sensor"

		command "reset"

		fingerprint type:"1001", mfr:"0109", prod:"201A", model:"1AA4"
	}

	simulator {
	}

	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821"
			state "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
		}
		valueTile("va", "device.va") {
			state "default", label:'${currentValue} kVAh'
		}
		valueTile("energy", "device.energy") {
			state "default", label:'${currentValue} kWh'
		}
		valueTile("power", "device.power") {
			state "default", label:'${currentValue} W'
		}
		valueTile("potential", "device.potential") {
			state "default", label:'${currentValue} V'
		}
		valueTile("current", "device.current") {
			state "default", label:'${currentValue} A'
		}
		valueTile("factor", "device.factor") {
			state "default", label:'${currentValue}'
		}
		standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Reset Usage', action:"reset"
		}
        standardTile("refresh", "command.refresh", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh", icon:"st.secondary.refresh"
		}

		main(["switch","power","energy"])
		details(["switch","energy","va","power","potential","current","factor","refresh","reset"])
	}

	preferences {
        input("secondsinterval", "number", title:"Auto Report Timing (60-255s (Default: 60s))"); //, required:false, displayDuringSetup: true)
        input("wattsinterval", "number", title:"Report when Wattage Changes (5-3600W (Default: 50W))"); //, required:false, displayDuringSetup: true)
    }
}

def updated() {
	configure()
}

// parse events into attributes
def parse(String description) {
	def result
	log.debug "Parsing '${description}'"
	if (description.startsWith("Err 106")) {
		if (state.sec) {
			result = createEvent(descriptionText:description, displayed:false)
		} else {
			result = createEvent(
					descriptionText: "Switch failed to complete the network security key exchange. If you are unable to receive data from it, you must remove it from your network and add it again.",
					eventType: "ALERT",
					name: "secureInclusion",
					value: "failed",
					displayed: true,
			)
		}
		state.sec = 0
	} else if (description == "updated") {
		return null
	} else {
		def cmd = zwave.parse(description, [0x25: 1, 0x31: 5, 0x32: 2, 0x5A: 1, 0x70: 1, 0x72: 2, 0x98: 1])

		if (cmd) {
			log.debug "Parsed '${cmd}'"
			result = zwaveEvent(cmd)
		}
	}

	result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical")
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical")
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	createEvent(name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false)
}

//security
def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	log.debug "Got encapsulated command $cmd"
    def encapsulatedCommand = cmd.encapsulatedCommand([0x25: 1, 0x5A: 1])
	if (encapsulatedCommand) {
		state.sec = 1
		return zwaveEvent(encapsulatedCommand)
	} else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
		createEvent(descriptionText: cmd.toString())
	}
}

//crc16
def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd)
{
	log .debug "Got command $cmd"
    def versions = [0x31: 5, 0x32: 2, 0x70: 1, 0x72: 2, 0x98: 1]
	def version = versions[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (!encapsulatedCommand) {
		log.debug "Could not extract command from $cmd"
	} else {
		zwaveEvent(encapsulatedCommand)
	}
}
def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "Unhandled: $cmd"
	null
}

def on() {
	commands([
		zwave.basicV1.basicSet(value: 0xFF),
		zwave.switchBinaryV1.switchBinaryGet()
	])
}

def off() {
	commands([
		zwave.basicV1.basicSet(value: 0x00),
		zwave.switchBinaryV1.switchBinaryGet()
	])
}

def poll() {
	refresh()
}

def refresh() {
	commands([
		zwave.switchBinaryV1.switchBinaryGet(),
		zwave.meterV2.meterGet(scale: 1),
		zwave.meterV2.meterGet(scale: 2),
		zwave.meterV2.meterGet(scale: 3),
		zwave.meterV2.meterGet(scale: 4),
		zwave.meterV2.meterGet(scale: 5),
		zwave.meterV2.meterGet(scale: 6),
    ])
}

def ping() {
	log.debug "ping() called"
	refresh()
}

private command(physicalgraph.zwave.Command cmd) {
	if (state.sec != 0) {
    	log.debug("INFO: Secure Command: $cmd")
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
    	log.debug("INFO: Insecure Command: $cmd")
        cmd.format()
	}
}

private commands(commands, delay=200) {
	delayBetween(commands.collect{ command(it) }, delay)
}

private secure(physicalgraph.zwave.Command cmd) {
	zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private crc16(physicalgraph.zwave.Command cmd) {
	//zwave.crc16encapV1.crc16Encap().encapsulate(cmd).format()
	"5601${cmd.format()}0000"
}

def zwaveEvent(physicalgraph.zwave.commands.meterv2.MeterReport cmd) {
	if (cmd.reserved02) {
    	if (cmd.scale == 0) {
            createEvent(name: "potential", value: cmd.scaledMeterValue, unit: "V")
        } else if (cmd.scale == 1) {
            createEvent(name: "current", value: cmd.scaledMeterValue, unit: "A")
        } else if (cmd.scale == 2) {
            createEvent(name: "factor", value: cmd.scaledMeterValue, unit: "pf")
        }
    } else {
        if (cmd.scale == 0) {
            createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
        } else if (cmd.scale == 1) {
            createEvent(name: "va", value: cmd.scaledMeterValue, unit: "kVAh")
        } else if (cmd.scale == 2) {
            createEvent(name: "power", value: cmd.scaledMeterValue, unit: "W")
        }
    }
}

def configure() {
	commands([
        zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: settings.secondsinterval ? settings.secondsinterval as Byte : 60), // Auto Report Timing (60-255, 60 default)
        zwave.configurationV1.configurationSet(parameterNumber: 2, size: 2, scaledConfigurationValue: settings.wattsinterval ? settings.wattsinterval as Short : 50), // Report when Wattage Changes (5-3600W (default 50))
        zwave.configurationV1.configurationGet(parameterNumber: 1), // Auto Report Timing (60-255, 60 default)
        zwave.configurationV1.configurationGet(parameterNumber: 2) // Report when Wattage Changes (5-3600W (default 50))
    ])
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
	if(cmd.parameterNumber == 1) {
    	if(cmd.configurationValue[0] != (settings.secondsinterval ? settings.secondsinterval : 60)) {
        	log.debug("WARNIING: Desired seconds interval ${settings.secondsinterval} not matching response ${cmd.configurationValue[0]}!")
        }
    } else if(cmd.parameterNumber == 2) {
    	int watts = cmd.configurationValue[0] * 256 + cmd.configurationValue[1]
    	if(watts != (settings.wattsinterval ? settings.wattsinterval : 50)) {
        	log.debug("WARNING: Desired watts interval ${settings.wattsinterval} not matching response ${watts}!")
        }
    }
}

def reset() {
	commands([
		zwave.meterV2.meterReset(),
		zwave.meterV2.meterGet(scale: 1),
		zwave.meterV2.meterGet(scale: 2)
	])
}
