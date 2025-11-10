#!/usr/bin/python3

#
# Copyright 2021, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This script simulates different combinations of scans and config to allow a tester to manually
# verify behavior in the Wi-Fi picker without setting up an actual test environment with real APs.
# This is especially useful for verifying interactions between scans and configs of multiple
# security types from the same security family, along with suggestions for the same networks.
#
# INSTRUCTIONS:
# 1) Connect an adb debuggable test device.
# 2) Open the test device and navigate to the Wi-Fi picker.
# 3) In main(), uncomment any test cases that you want to test.
# 4) If testing suggestions INSTEAD of configs, only set ADD_SUGGESTION_INSTEAD_OF_CONFIG to True.
# 5) If testing suggestions AND configs, only set ADD_IDENTICAL_SUGGESTION to True.
# 6) Run this script 'py simulate_wifi.py'
# 7) Follow the prompts from the script. The prompts will ask you to verify the behavior of the
#    Wi-Fi picker through user interaction and visual confirmation.
#
# NOTE: Suggestions may take several seconds to appear. This is expected since it may be some scans
#       cycles until WifiManager#getWifiConfigForMatchedNetworkSuggestionsSharedWithUser() returns
#       the matching suggestion.

import subprocess
import sys
import time

OPEN_SSID = "fakeOpen"
OWE_SSID = "fakeOwe"
OPEN_OWE_SSID = "fakeOpenOwe"
WPA2_SSID = "fakeWpa2"
WPA3_SSID = "fakeWpa3"
WPA2_WPA3_SSID = "fakeWpa2Wpa3"

# only one of these should be True (possibly none for just adding base configuration)
ADD_SUGGESTION_INSTEAD_OF_CONFIG = False
ADD_IDENTICAL_SUGGESTION = False

def main() -> None:
  root()
  time.sleep(5)

  # Single Open scan
  # testOpenScanNoConfigs()
  # testOpenScanOpenConfig()
  # testOpenScanOweConfig()
  # testOpenScanOpenOweConfig()

  # Single OWE scan
  # testOweScanNoConfigs()
  # testOweScanOpenConfig()
  # testOweScanOweConfig()
  # testOweScanOpenOweConfig()

  # Single Open/OWE scan
  # testOpenOweScanNoConfigs()
  # testOpenOweScanOpenConfig()
  # testOpenOweScanOweConfig()
  # testOpenOweScanOpenOweConfig()

  # Open scan and OWE scan
  # testOpenScanOweScanNoConfigs()
  # testOpenScanOweScanOpenConfig()
  # testOpenScanOweScanOweConfig()
  # testOpenScanOweScanOpenOweConfig()

  # Open scan and Open/OWE scan
  # testOpenScanOpenOweScanNoConfigs()
  # testOpenScanOpenOweScanOpenConfig()
  # testOpenScanOpenOweScanOweConfig()
  # testOpenScanOpenOweScanOpenOweConfig()

  # Open/OWE scan and OWE scan
  # testOpenOweScanOweScanNoConfigs()
  # testOpenOweScanOweScanOpenConfig()
  # testOpenOweScanOweScanOweConfig()
  # testOpenOweScanOweScanOpenOweConfig()

  # Open, Open/OWE, and OWE scans
  # testOpenScanOpenOweScanOweScanNoConfigs()
  # testOpenScanOpenOweScanOweScanOpenConfig()
  # testOpenScanOpenOweScanOweScanOweConfig()
  # testOpenScanOpenOweScanOweScanOpenOweConfig()

  # Single WPA2 scan
  # testWpa2ScanNoConfigs()
  # testWpa2ScanWpa2Config()
  # testWpa2ScanWpa3Config()
  # testWpa2ScanWpa2Wpa3Config()

  # Single WPA3 scan
  # testWpa3ScanNoConfigs()
  # testWpa3ScanWpa2Config()
  # testWpa3ScanWpa3Config()
  # testWpa3ScanWpa2Wpa3Config()

  # Single WP2/WPA3 scan
  # testWpa2Wpa3ScanNoConfigs()
  # testWpa2Wpa3ScanWpa2Config()
  # testWpa2Wpa3ScanWpa3Config()
  # testWpa2Wpa3ScanWpa2Wpa3Config()

  # WPA2 scan and WPA3 scan
  # testWpa2ScanWpa3ScanNoConfigs()
  # testWpa2ScanWpa3ScanWpa2Config()
  # testWpa2ScanWpa3ScanWpa3Config()
  # testWpa2ScanWpa3ScanWpa2Wpa3Config()

  # WPA2 scan and WPA2/WPA3 scan
  # testWpa2ScanWpa2Wpa3ScanNoConfigs()
  # testWpa2ScanWpa2Wpa3ScanWpa2Config()
  # testWpa2ScanWpa2Wpa3ScanWpa3Config()
  # testWpa2ScanWpa2Wpa3ScanWpa2Wpa3Config()

  # WPA2/WPA3 scan and WPA3 scan
  # testWpa2Wpa3ScanWpa3ScanNoConfigs()
  # testWpa2Wpa3ScanWpa3ScanWpa2Config()
  # testWpa2Wpa3ScanWpa3ScanWpa3Config()
  # testWpa2Wpa3ScanWpa3ScanWpa2Wpa3Config()

  # WPA2, WPA2/WPA3, and WPA3 scans
  # testWpa2ScanWpa2Wpa3ScanWpa3ScanNoConfigs()
  # testWpa2ScanWpa2Wpa3ScanWpa3ScanWpa2Config()
  # testWpa2ScanWpa2Wpa3ScanWpa3ScanWpa3Config()
  # testWpa2ScanWpa2Wpa3ScanWpa3ScanWpa2Wpa3Config()

  return 0


def testTemplate(method_name, fake_scans, fake_configs, pre_instructions, post_instructions) -> None:
  print("")
  print("")
  print("****** Test: " + method_name)
  print("** Resetting all scans/configs")
  settings_reset()
  print("** Starting to fake scans")
  fake_scans()
  startFakingScans()
  startScan()
  time.sleep(5)
  print("** Fake scan results:")
  for scan in getScanResults():
    print(scan.decode())
  print("** Inserting fake configurations (and optionally suggestions)")
  fake_configs()
  print("** Pre action saved networks:")
  for config in getSavedConfigs():
    print(config.decode())
  if ADD_IDENTICAL_SUGGESTION or ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    print("** Pre action suggestions:")
    for sugg in getSuggestions():
      print(sugg.decode())
  print("---> " + pre_instructions)
  input("** Then press Enter to continue ...")
  print("** Post action saved networks:")
  for config in getSavedConfigs():
    print(config.decode())
  print("---> " + post_instructions)
  input("** Press Enter to continue ...")

#
# Single WPA2 scan: different WPA2/WPA3 configurations
#

def testWpa2ScanNoConfigs() -> None:
  testTemplate(
    testWpa2ScanNoConfigs.__name__,
    lambda : addFakeWpa2Scan(WPA2_SSID, "80:01:02:03:04:05"),
    lambda : print("no configs added"),
    "Open picker: select " + WPA2_SSID,
    "Should ask for passphrase, then be WPA2 (+ WPA3^ if device supports auto-upgrade)"
  )

def testWpa2ScanWpa2Config() -> None:
  testTemplate(
    testWpa2ScanWpa2Config.__name__,
    lambda : addFakeWpa2Scan(WPA2_SSID, "80:01:02:03:04:05"),
    lambda : addWpa2Config(WPA2_SSID),
    "Open picker: select " + WPA2_SSID,
    "Should not ask for passphrase, then be WPA2 only if device does not support auto-upgrade (otherwise + WPA3^)"
  )

def testWpa2ScanWpa3Config() -> None:
  testTemplate(
    testWpa2ScanWpa3Config.__name__,
    lambda : addFakeWpa2Scan(WPA2_SSID, "80:01:02:03:04:05"),
    lambda : addWpa3Config(WPA2_SSID),
    "Open picker: select " + WPA2_SSID,
    "Should ask for passphrase, then be WPA2+WPA3"
  )

def testWpa2ScanWpa2Wpa3Config() -> None:
  testTemplate(
    testWpa2ScanWpa2Wpa3Config.__name__,
    lambda : addFakeWpa2Scan(WPA2_SSID, "80:01:02:03:04:05"),
    lambda : (addWpa2Config(WPA2_SSID),
              addWpa3Config(WPA2_SSID)),
    "Open picker: select " + WPA2_SSID,
    "Should not ask for passphrase, then be WPA2+WPA3"
  )

#
# Single WPA3 scan: different WPA2/WPA3 configurations
#

def testWpa3ScanNoConfigs() -> None:
  testTemplate(
    testWpa3ScanNoConfigs.__name__,
    lambda : addFakeWpa3Scan(WPA3_SSID, "80:01:02:03:04:05"),
    lambda : print("no configs added"),
    "Open picker: select " + WPA3_SSID,
    "Should ask for passphrase, then be WPA3"
  )

def testWpa3ScanWpa2Config() -> None:
  testTemplate(
    testWpa3ScanWpa2Config.__name__,
    lambda : addFakeWpa3Scan(WPA3_SSID, "80:01:02:03:04:05"),
    lambda : addWpa2Config(WPA3_SSID),
    "Open picker: select " + WPA3_SSID,
    "If no auto-upgrade: Should ask for passphrase, then be WPA2 + WPA3, otherwise should not ask for passphrase and be WPA2+WPA3^"
  )

def testWpa3ScanWpa3Config() -> None:
  testTemplate(
    testWpa3ScanWpa3Config.__name__,
    lambda : addFakeWpa3Scan(WPA3_SSID, "80:01:02:03:04:05"),
    lambda : addWpa3Config(WPA3_SSID),
    "Open picker: select " + WPA3_SSID,
    "Should not ask for passphrase, then be WPA3"
  )

def testWpa3ScanWpa2Wpa3Config() -> None:
  testTemplate(
    testWpa3ScanWpa2Wpa3Config.__name__,
    lambda : addFakeWpa3Scan(WPA3_SSID, "80:01:02:03:04:05"),
    lambda : (addWpa2Config(WPA3_SSID),
              addWpa3Config(WPA3_SSID)),
    "Open picker: select " + WPA3_SSID,
    "Should not ask for passphrase, then be WPA2+WPA3"
  )

#
# Single WPA2/WPA3 scan: different WPA2/WPA3 configurations
#

def testWpa2Wpa3ScanNoConfigs() -> None:
  testTemplate(
    testWpa2Wpa3ScanNoConfigs.__name__,
    lambda : addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
    lambda : print("no configs added"),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should ask for passphrase, then be WPA2 if no auto-upgrade, WPA2+WPA3^ if auto-upgrade"
  )

def testWpa2Wpa3ScanWpa2Config() -> None:
  testTemplate(
    testWpa2Wpa3ScanWpa2Config.__name__,
    lambda : addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
    lambda : addWpa2Config(WPA2_WPA3_SSID),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA2 if no auto-upgrade (otherwise WPA2+WPA3^)"
  )

def testWpa2Wpa3ScanWpa3Config() -> None:
  testTemplate(
    testWpa2Wpa3ScanWpa3Config.__name__,
    lambda : addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
    lambda : addWpa3Config(WPA2_WPA3_SSID),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA3"
  )

def testWpa2Wpa3ScanWpa2Wpa3Config() -> None:
  testTemplate(
    testWpa2Wpa3ScanWpa2Wpa3Config.__name__,
    lambda : addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
    lambda : (addWpa2Config(WPA2_WPA3_SSID),
              addWpa3Config(WPA2_WPA3_SSID)),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA2+WPA3"
  )

#
# Single WPA2 scan and single WPA3 scan: different WPA2/WPA3 configurations
#

def testWpa2ScanWpa3ScanNoConfigs() -> None:
  testTemplate(
    testWpa2ScanWpa3ScanNoConfigs.__name__,
    lambda : (addFakeWpa2Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa3Scan(WPA2_WPA3_SSID, "80:02:02:03:04:06")),
    lambda : print("no configs added"),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should ask for passphrase, then be WPA2 if no auto-upgrade, WPA2+WPA3^ if auto-upgrade"
  )

def testWpa2ScanWpa3ScanWpa2Config() -> None:
  testTemplate(
    testWpa2ScanWpa3ScanWpa2Config.__name__,
    lambda : (addFakeWpa2Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa3Scan(WPA2_WPA3_SSID, "80:02:02:03:04:06")),
    lambda : addWpa2Config(WPA2_WPA3_SSID),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA2 if no auto-upgrade (otherwise WPA2+WPA3^)"
  )

def testWpa2ScanWpa3ScanWpa3Config() -> None:
  testTemplate(
    testWpa2ScanWpa3ScanWpa3Config.__name__,
    lambda : (addFakeWpa2Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa3Scan(WPA2_WPA3_SSID, "80:02:02:03:04:06")),
    lambda : addWpa3Config(WPA2_WPA3_SSID),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA3"
  )

def testWpa2ScanWpa3ScanWpa2Wpa3Config() -> None:
  testTemplate(
    testWpa2ScanWpa3ScanWpa2Wpa3Config.__name__,
    lambda : (addFakeWpa2Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa3Scan(WPA2_WPA3_SSID, "80:02:02:03:04:06")),
    lambda : (addWpa2Config(WPA2_WPA3_SSID),
              addWpa3Config(WPA2_WPA3_SSID)),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA2+WPA3"
  )

#
# Single WPA2 scan and single WPA2+WPA3 transition scan: different WPA2/WPA3 configurations
#

def testWpa2ScanWpa2Wpa3ScanNoConfigs() -> None:
  testTemplate(
    testWpa2ScanWpa2Wpa3ScanNoConfigs.__name__,
    lambda : (addFakeWpa2Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:02:02:03:04:06")),
    lambda : print("no configs added"),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should ask for passphrase, then be WPA2 if no auto-upgrade, WPA2+WPA3^ if auto-upgrade"
  )

def testWpa2ScanWpa2Wpa3ScanWpa2Config() -> None:
  testTemplate(
    testWpa2ScanWpa2Wpa3ScanWpa2Config.__name__,
    lambda : (addFakeWpa2Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:02:02:03:04:06")),
    lambda : addWpa2Config(WPA2_WPA3_SSID),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA2 if no auto-upgrade (otherwise WPA2+WPA3^)"
  )

def testWpa2ScanWpa2Wpa3ScanWpa3Config() -> None:
  testTemplate(
    testWpa2ScanWpa2Wpa3ScanWpa3Config.__name__,
    lambda : (addFakeWpa2Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:02:02:03:04:06")),
    lambda : addWpa3Config(WPA2_WPA3_SSID),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA3"
  )

def testWpa2ScanWpa2Wpa3ScanWpa2Wpa3Config() -> None:
  testTemplate(
    testWpa2ScanWpa2Wpa3ScanWpa2Wpa3Config.__name__,
    lambda : (addFakeWpa2Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:02:02:03:04:06")),
    lambda : (addWpa2Config(WPA2_WPA3_SSID),
              addWpa3Config(WPA2_WPA3_SSID)),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA2+WPA3"
  )

#
# Single WPA2+WPA3 transition scan and single WPA3 scan: different WPA2/WPA3 configurations
#

def testWpa2Wpa3ScanWpa3ScanNoConfigs() -> None:
  testTemplate(
    testWpa2Wpa3ScanWpa3ScanNoConfigs.__name__,
    lambda : (addFakeWpa3Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:02:02:03:04:06")),
    lambda : print("no configs added"),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should ask for passphrase, then be WPA2 if no auto-upgrade, WPA2+WPA3^ if auto-upgrade"
  )

def testWpa2Wpa3ScanWpa3ScanWpa2Config() -> None:
  testTemplate(
    testWpa2Wpa3ScanWpa3ScanWpa2Config.__name__,
    lambda : (addFakeWpa3Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:02:02:03:04:06")),
    lambda : addWpa2Config(WPA2_WPA3_SSID),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA2 if no auto-upgrade (otherwise WPA2+WPA3^)"
  )

def testWpa2Wpa3ScanWpa3ScanWpa3Config() -> None:
  testTemplate(
    testWpa2Wpa3ScanWpa3ScanWpa3Config.__name__,
    lambda : (addFakeWpa3Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:02:02:03:04:06")),
    lambda : addWpa3Config(WPA2_WPA3_SSID),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA3"
  )

def testWpa2Wpa3ScanWpa3ScanWpa2Wpa3Config() -> None:
  testTemplate(
    testWpa2Wpa3ScanWpa3ScanWpa2Wpa3Config.__name__,
    lambda : (addFakeWpa3Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:02:02:03:04:06")),
    lambda : (addWpa2Config(WPA2_WPA3_SSID),
              addWpa3Config(WPA2_WPA3_SSID)),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA2+WPA3"
  )

#
# Single WPA2 scan, single WPA2+WPA3 transition scan, and single WPA3 scan: different WPA2/WPA3 configurations
#

def testWpa2ScanWpa2Wpa3ScanWpa3ScanNoConfigs() -> None:
  testTemplate(
    testWpa2ScanWpa2Wpa3ScanWpa3ScanNoConfigs.__name__,
    lambda : (addFakeWpa2Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa3Scan(WPA2_WPA3_SSID, "80:02:02:03:04:06"),
              addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:03:02:03:04:07")),
    lambda : print("no configs added"),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should ask for passphrase, then be WPA2 if no auto-upgrade, WPA2+WPA3^ if auto-upgrade"
  )

def testWpa2ScanWpa2Wpa3ScanWpa3ScanWpa2Config() -> None:
  testTemplate(
    testWpa2ScanWpa2Wpa3ScanWpa3ScanWpa2Config.__name__,
    lambda : (addFakeWpa2Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa3Scan(WPA2_WPA3_SSID, "80:02:02:03:04:06"),
              addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:03:02:03:04:07")),
    lambda : addWpa2Config(WPA2_WPA3_SSID),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA2 if no auto-upgrade (otherwise WPA2+WPA3^)"
  )

def testWpa2ScanWpa2Wpa3ScanWpa3ScanWpa3Config() -> None:
  testTemplate(
    testWpa2ScanWpa2Wpa3ScanWpa3ScanWpa3Config.__name__,
    lambda : (addFakeWpa2Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa3Scan(WPA2_WPA3_SSID, "80:02:02:03:04:06"),
              addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:03:02:03:04:07")),
    lambda : addWpa3Config(WPA2_WPA3_SSID),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA3"
  )

def testWpa2ScanWpa2Wpa3ScanWpa3ScanWpa2Wpa3Config() -> None:
  testTemplate(
    testWpa2ScanWpa2Wpa3ScanWpa3ScanWpa2Wpa3Config.__name__,
    lambda : (addFakeWpa2Scan(WPA2_WPA3_SSID, "80:01:02:03:04:05"),
              addFakeWpa3Scan(WPA2_WPA3_SSID, "80:02:02:03:04:06"),
              addFakeWpa2Wpa3TransitionScan(WPA2_WPA3_SSID, "80:03:02:03:04:07")),
    lambda : (addWpa2Config(WPA2_WPA3_SSID),
              addWpa3Config(WPA2_WPA3_SSID)),
    "Open picker: select " + WPA2_WPA3_SSID,
    "Should not ask for passphrase, then be WPA2+WPA3"
  )

#
# Single open scan: different open/OWE configurations
#

def testOpenScanNoConfigs() -> None:
  testTemplate(
    testOpenScanNoConfigs.__name__,
    lambda : addFakeOpenScan(OPEN_SSID, "80:01:02:03:04:05"),
    lambda : print("no configs added"),
    "Open picker: select " + OPEN_SSID,
    "Should be Open + OWE^ if device supports auto-upgrade"
  )

def testOpenScanOpenConfig() -> None:
  testTemplate(
    testOpenScanOpenConfig.__name__,
    lambda : addFakeOpenScan(OPEN_SSID, "80:01:02:03:04:05"),
    lambda : addOpenConfig(OPEN_SSID),
    "Open picker: select " + OPEN_SSID,
    "Should be Open only if device does not support auto-upgrade (otherwise + OWE^)"
  )

def testOpenScanOweConfig() -> None:
  testTemplate(
    testOpenScanOweConfig.__name__,
    lambda : addFakeOpenScan(OPEN_SSID, "80:01:02:03:04:05"),
    lambda : addOweConfig(OPEN_SSID),
    "Open picker: select " + OPEN_SSID,
    "Should be Open + OWE"
  )

def testOpenScanOpenOweConfig() -> None:
  testTemplate(
    testOpenScanOpenOweConfig.__name__,
    lambda : addFakeOpenScan(OPEN_SSID, "80:01:02:03:04:05"),
    lambda : (addOpenConfig(OPEN_SSID),
              addOweConfig(OPEN_SSID)),
    "Open picker: select " + OPEN_SSID,
    "Should be Open + OWE"
  )

#
# Single OWE scan: different open/OWE configurations
#

def testOweScanNoConfigs() -> None:
  testTemplate(
    testOweScanNoConfigs.__name__,
    lambda : addFakeOweScan(OWE_SSID, "80:01:02:03:04:05"),
    lambda : print("no configs added"),
    "Open picker: select " + OWE_SSID,
    "Should be OWE"
  )

def testOweScanOpenConfig() -> None:
  testTemplate(
    testOweScanOpenConfig.__name__,
    lambda : addFakeOweScan(OWE_SSID, "80:01:02:03:04:05"),
    lambda : addOpenConfig(OWE_SSID),
    "Open picker: select " + OWE_SSID,
    "Should be Open+OWE^"
  )

def testOweScanOweConfig() -> None:
  testTemplate(
    testOweScanOweConfig.__name__,
    lambda : addFakeOweScan(OWE_SSID, "80:01:02:03:04:05"),
    lambda : addOweConfig(OWE_SSID),
    "Open picker: select " + OWE_SSID,
    "Should be OWE"
  )

def testOweScanOpenOweConfig() -> None:
  testTemplate(
    testOweScanOpenOweConfig.__name__,
    lambda : addFakeOweScan(OWE_SSID, "80:01:02:03:04:05"),
    lambda : (addOpenConfig(OWE_SSID),
              addOweConfig(OWE_SSID)),
    "Open picker: select " + OWE_SSID,
    "Should be Open + OWE"
  )

#
# Single Open/OWE transition mode scan: different open/OWE configurations
#

def testOpenOweScanNoConfigs() -> None:
  testTemplate(
    testOpenOweScanNoConfigs.__name__,
    lambda : addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
    lambda : print("no configs added"),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open+OWE"
  )

def testOpenOweScanOpenConfig() -> None:
  testTemplate(
    testOpenOweScanOpenConfig.__name__,
    lambda : addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
    lambda : addOpenConfig(OPEN_OWE_SSID),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open+OWE^"
  )

def testOpenOweScanOweConfig() -> None:
  testTemplate(
    testOpenOweScanOweConfig.__name__,
    lambda : addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
    lambda : addOweConfig(OPEN_OWE_SSID),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be OWE"
  )

def testOpenOweScanOpenOweConfig() -> None:
  testTemplate(
    testOpenOweScanOpenOweConfig.__name__,
    lambda : addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
    lambda : (addOpenConfig(OPEN_OWE_SSID),
              addOweConfig(OPEN_OWE_SSID)),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open + OWE"
  )

#
# Single Open scan and single OWE scan: different Open/OWE configurations
#

def testOpenScanOweScanNoConfigs() -> None:
  testTemplate(
    testOpenScanOweScanNoConfigs.__name__,
    lambda : (addFakeOpenScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOweScan(OPEN_OWE_SSID, "80:02:02:03:04:06")),
    lambda : print("no configs added"),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open+OWE"
  )

def testOpenScanOweScanOpenConfig() -> None:
  testTemplate(
    testOpenScanOweScanOpenConfig.__name__,
    lambda : (addFakeOpenScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOweScan(OPEN_OWE_SSID, "80:02:02:03:04:06")),
    lambda : addOpenConfig(OPEN_OWE_SSID),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open if no auto-upgrade (otherwise Open+OWE^)"
  )

def testOpenScanOweScanOweConfig() -> None:
  testTemplate(
    testOpenScanOweScanOweConfig.__name__,
    lambda : (addFakeOpenScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOweScan(OPEN_OWE_SSID, "80:02:02:03:04:06")),
    lambda : addOweConfig(OPEN_OWE_SSID),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be OWE"
  )

def testOpenScanOweScanOpenOweConfig() -> None:
  testTemplate(
    testOpenScanOweScanOpenOweConfig.__name__,
    lambda : (addFakeOpenScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOweScan(OPEN_OWE_SSID, "80:02:02:03:04:06")),
    lambda : (addOpenConfig(OPEN_OWE_SSID),
              addOweConfig(OPEN_OWE_SSID)),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open+OWE"
  )

#
# Single Open scan and single Open+OWE transition scan: different Open/OWE configurations
#

def testOpenScanOpenOweScanNoConfigs() -> None:
  testTemplate(
    testOpenScanOpenOweScanNoConfigs.__name__,
    lambda : (addFakeOpenScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:02:02:03:04:06")),
    lambda : print("no configs added"),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open+OWE"
  )

def testOpenScanOpenOweScanOpenConfig() -> None:
  testTemplate(
    testOpenScanOpenOweScanOpenConfig.__name__,
    lambda : (addFakeOpenScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:02:02:03:04:06")),
    lambda : addOpenConfig(OPEN_OWE_SSID),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open if no auto-upgrade (otherwise Open+OWE^)"
  )

def testOpenScanOpenOweScanOweConfig() -> None:
  testTemplate(
    testOpenScanOpenOweScanOweConfig.__name__,
    lambda : (addFakeOpenScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:02:02:03:04:06")),
    lambda : addOweConfig(OPEN_OWE_SSID),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be OWE"
  )

def testOpenScanOpenOweScanOpenOweConfig() -> None:
  testTemplate(
    testOpenScanOpenOweScanOpenOweConfig.__name__,
    lambda : (addFakeOpenScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:02:02:03:04:06")),
    lambda : (addOpenConfig(OPEN_OWE_SSID),
              addOweConfig(OPEN_OWE_SSID)),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open+OWE"
  )

#
# Single Open+OWE transition scan and single OWE scan: different Open/OWE configurations
#

def testOpenOweScanOweScanNoConfigs() -> None:
  testTemplate(
    testOpenOweScanOweScanNoConfigs.__name__,
    lambda : (addFakeOweScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:02:02:03:04:06")),
    lambda : print("no configs added"),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open+OWE"
  )

def testOpenOweScanOweScanOpenConfig() -> None:
  testTemplate(
    testOpenOweScanOweScanOpenConfig.__name__,
    lambda : (addFakeOweScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:02:02:03:04:06")),
    lambda : addOpenConfig(OPEN_OWE_SSID),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open if no auto-upgrade (otherwise Open+OWE^)"
  )

def testOpenOweScanOweScanOweConfig() -> None:
  testTemplate(
    testOpenOweScanOweScanOweConfig.__name__,
    lambda : (addFakeOweScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:02:02:03:04:06")),
    lambda : addOweConfig(OPEN_OWE_SSID),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be OWE"
  )

def testOpenOweScanOweScanOpenOweConfig() -> None:
  testTemplate(
    testOpenOweScanOweScanOpenOweConfig.__name__,
    lambda : (addFakeOweScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:02:02:03:04:06")),
    lambda : (addOpenConfig(OPEN_OWE_SSID),
              addOweConfig(OPEN_OWE_SSID)),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open+OWE"
  )

#
# Single Open scan, single Open+OWE transition scan, and single OWE scan: different Open/OWE configurations
#

def testOpenScanOpenOweScanOweScanNoConfigs() -> None:
  testTemplate(
    testOpenScanOpenOweScanOweScanNoConfigs.__name__,
    lambda : (addFakeOpenScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOweScan(OPEN_OWE_SSID, "80:02:02:03:04:06"),
              addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:03:02:03:04:07")),
    lambda : print("no configs added"),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open+OWE"
  )

def testOpenScanOpenOweScanOweScanOpenConfig() -> None:
  testTemplate(
    testOpenScanOpenOweScanOweScanOpenConfig.__name__,
    lambda : (addFakeOpenScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOweScan(OPEN_OWE_SSID, "80:02:02:03:04:06"),
              addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:03:02:03:04:07")),
    lambda : addOpenConfig(OPEN_OWE_SSID),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open if no auto-upgrade (otherwise Open+OWE^)"
  )

def testOpenScanOpenOweScanOweScanOweConfig() -> None:
  testTemplate(
    testOpenScanOpenOweScanOweScanOweConfig.__name__,
    lambda : (addFakeOpenScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOweScan(OPEN_OWE_SSID, "80:02:02:03:04:06"),
              addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:03:02:03:04:07")),
    lambda : addOweConfig(OPEN_OWE_SSID),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be OWE"
  )

def testOpenScanOpenOweScanOweScanOpenOweConfig() -> None:
  testTemplate(
    testOpenScanOpenOweScanOweScanOpenOweConfig.__name__,
    lambda : (addFakeOpenScan(OPEN_OWE_SSID, "80:01:02:03:04:05"),
              addFakeOweScan(OPEN_OWE_SSID, "80:02:02:03:04:06"),
              addFakeOpenOweTransitionScan(OPEN_OWE_SSID, "80:03:02:03:04:07")),
    lambda : (addOpenConfig(OPEN_OWE_SSID),
              addOweConfig(OPEN_OWE_SSID)),
    "Open picker: select " + OPEN_OWE_SSID,
    "Should be Open+OWE"
  )

def root() -> None:
  subprocess.run(["adb", "root"])

def settings_reset() -> None:
  subprocess.run(["adb", "shell", "cmd", "wifi", "settings-reset"])

def getSavedConfigs() -> str:
  return subprocess.check_output(["adb", "shell", "cmd", "wifi", "list-networks"]).splitlines()

def getSuggestions() -> str:
  return subprocess.check_output(["adb", "shell", "cmd", "wifi", "list-suggestions"]).splitlines()

def getScanResults() -> str:
  return subprocess.check_output(["adb", "shell", "cmd", "wifi", "list-scan-results"]).splitlines()

def startScan() -> None:
  subprocess.run(["adb", "shell", "cmd", "wifi", "start-scan"])

def startFakingScans() -> None:
  subprocess.run(["adb", "shell", "cmd", "wifi", "start-faking-scans"])

def resetFakeScans() -> None:
  subprocess.run(["adb", "shell", "cmd", "wifi", "reset-fake-scans"])

#
# Add fake scans
#

def addFakeScan(ssid: str, bssid: str, cap: str, freq: int = 2412, dbm: int = -55) -> None:
  subprocess.run(["adb", "shell", "cmd", "wifi", "add-fake-scan", ssid, bssid, cap, str(freq), str(dbm)])

def addFakeOpenScan(ssid: str, bssid: str, freq: int = 2412, dbm: int = -55) -> None:
  addFakeScan(ssid, bssid, "[ESS]", freq, dbm)

def addFakeWpa2Scan(ssid: str, bssid: str, freq: int = 2412, dbm: int = -55) -> None:
  addFakeScan(ssid, bssid, "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]", freq, dbm)

def addFakeWpa3Scan(ssid: str, bssid: str, freq: int = 2412, dbm: int = -55) -> None:
  addFakeScan(ssid, bssid, "[RSN-SAE+FT/SAE-CCMP][ESS]", freq, dbm)

def addFakeOweScan(ssid: str, bssid: str, freq: int = 2412, dbm: int = -55) -> None:
  addFakeScan(ssid, bssid, "[RSN-OWE-CCMP]", freq, dbm)

def addFakeWpa2Wpa3TransitionScan(ssid: str, bssid: str, freq: int = 2412, dbm: int = -55) -> None:
  addFakeScan(ssid, bssid, "[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS][MFPC]", freq, dbm)

def addFakeOpenOweTransitionScan(ssid: str, bssid: str, freq: int = 2412, dbm: int = -55) -> None:
  addFakeScan(ssid, bssid, "[RSN-OWE_TRANSITION-CCMP][ESS]", freq, dbm)

def addFakePasspointScan(ssid: str, bssid: str, freq: int = 2412, dbm: int = -55) -> None:
  addFakeScan(ssid, bssid, "[WPA2-EAP/SHA1-CCMP][RSN-EAP/SHA1-CCMP][ESS][MFPR][MFPC][PASSPOINT]", freq, dbm)

#
# Add configs
#

def addOpenConfig(ssid: str) -> None:
  if not ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-network", ssid, "open"])
  if ADD_IDENTICAL_SUGGESTION or ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-suggestion", ssid, "open"])

def addOweConfig(ssid: str) -> None:
  if not ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-network", ssid, "owe"])
  if ADD_IDENTICAL_SUGGESTION or ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-suggestion", ssid, "owe"])

def addOpenOweConfig(ssid: str) -> None:
  if not ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-network", ssid, "open"])
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-network", ssid, "owe"])
  if ADD_IDENTICAL_SUGGESTION or ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-suggestion", ssid, "open"])
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-suggestion", ssid, "owe"])

def addWpa2Config(ssid: str) -> None:
  if not ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-network", ssid, "wpa2", "SomePassphrase"])
  if ADD_IDENTICAL_SUGGESTION or ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-suggestion", ssid, "wpa2", "SomePassphrase", "-s"])

def addWpa3Config(ssid: str) -> None:
  if not ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-network", ssid, "wpa3", "SomePassphrase"])
  if ADD_IDENTICAL_SUGGESTION or ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-suggestion", ssid, "wpa3", "SomePassphrase", "-s"])

def addWpa2Wpa3Config(ssid: str) -> None:
  if not ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-network", ssid, "wpa2", "SomePassphrase"])
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-network", ssid, "wpa3", "SomePassphrase"])
  if ADD_IDENTICAL_SUGGESTION or ADD_SUGGESTION_INSTEAD_OF_CONFIG:
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-suggestion", ssid, "wpa2", "SomePassphrase", "-s"])
    subprocess.run(["adb", "shell", "cmd", "wifi", "add-suggestion", ssid, "wpa3", "SomePassphrase", "-s"])

if __name__ == '__main__':
    exit_code = main()
    sys.exit(exit_code)



