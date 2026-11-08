#!/usr/bin/env python3
"""Exercise real system PIN and owner removal on an explicitly selected disposable emulator."""
import argparse
import json
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--verify-after-reboot', action='store_true', help='Resume final checks after an interrupted UI transition')
parser.add_argument('--baseline', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--home-label', default='Pixel Launcher', help='Ordinary launcher shown by Android Home settings')
parser.add_argument('--pin', default='2468', help='PIN of the disposable emulator, never a personal device')
args = parser.parse_args()
adb = ['adb', '-s', args.serial]
args.output.mkdir(parents=True, exist_ok=True)
baseline = json.loads(args.baseline.read_text())


def shell(*command):
    return subprocess.check_output(adb + ['shell', shlex.join(command)], text=True, timeout=25).strip()


def wait_for(check, description, timeout=20):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if check():
                return
        except AssertionError as error:
            if not str(error).startswith('No UI hierarchy:'):
                raise
        time.sleep(0.25)
    raise AssertionError('Timed out: ' + description)


def ui():
    text = subprocess.check_output(adb + ['shell', '-tt', 'uiautomator dump /dev/tty'], text=True, timeout=20)
    match = re.search(r'<\?xml.*?</hierarchy>', text, re.S)
    if not match:
        raise AssertionError('No UI hierarchy: ' + text[-200:])
    return ET.fromstring(match.group())


def bounds(node):
    return list(map(int, re.findall(r'\d+', node.attrib['bounds'])))


def tap(node):
    x1, y1, x2, y2 = bounds(node)
    shell('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def tap_label(label):
    for _ in range(6):
        tree = ui()
        for node in tree.iter('node'):
            if label in (node.attrib.get('text'), node.attrib.get('content-desc')):
                if bounds(node)[3] > bounds(node)[1]:
                    tap(node)
                    return
        scroll = next((n for n in tree.iter('node') if n.attrib.get('scrollable') == 'true'), None)
        if scroll is None:
            break
        x1, y1, x2, y2 = bounds(scroll)
        shell('input', 'swipe', str((x1 + x2) // 2), str(y2 - (y2-y1)//5),
              str((x1 + x2) // 2), str(y1 + (y2-y1)//5), '350')
    raise AssertionError('Missing UI action: ' + label)


def pin(value):
    wait_for(lambda: any(n.attrib.get('class') == 'android.widget.EditText' for n in ui().iter('node')), 'system PIN field')
    tree = ui()
    field = next(n for n in tree.iter('node') if n.attrib.get('class') == 'android.widget.EditText')
    assert field.attrib.get('package') == 'com.android.systemui', 'PIN must be verified by Android System UI'
    tap(field)
    shell('input', 'text', value)
    shell('input', 'keyevent', 'KEYCODE_ENTER')


def lock_mode():
    return re.search(r'mLockTaskModeState=(\w+)', shell('dumpsys', 'activity', 'activities')).group(1)


def snapshot(name):
    for service in ['device_policy', 'user', 'activity', 'backup']:
        (args.output / f'{name}-{service}.txt').write_text(shell('dumpsys', service))
    (args.output / f'{name}.xml').write_bytes(ET.tostring(ui(), encoding='utf-8'))
    (args.output / f'{name}.png').write_bytes(subprocess.check_output(adb + ['exec-out', 'screencap', '-p'], timeout=20))


def verify_unmanaged():
    assert shell('dpm', 'list-owners') == 'no owners'
    assert lock_mode() == 'NONE'
    assert shell('bmgr', 'activated') == 'Backup Manager currently activated'
    policy = shell('dumpsys', 'device_policy')
    assert not re.search(r'^\s*ru\.domovoy/\.kiosk\.PanelDeviceAdmin:', policy, re.M)
    users = shell('dumpsys', 'user').split('Device properties:')[0]
    for section in ['Restrictions', 'Device policy global restrictions', 'Device policy local restrictions', 'Effective restrictions']:
        value = re.search(r'^\s*' + section + r':\s*\n\s*(\S+)', users, re.M).group(1)
        assert value in ('none', 'null'), (section, value)
    assert 'Device managed: false' in shell('dumpsys', 'user')
    def home():
        return shell('cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME').splitlines()[-1]
    wait_for(lambda: home() == baseline['home'], 'original Home restored')
    assert shell('settings', 'get', 'secure', 'android_id') == baseline['android_id']
    assert shell('sha256sum', baseline['sentinel']).split()[0] == baseline['sentinel_sha256']
    assert shell('run-as', 'ru.domovoy', 'cat', 'files/owner-removal-proof') == 'retained-private-data'
    prefs = ET.fromstring(shell('run-as', 'ru.domovoy', 'cat', 'shared_prefs/panel-kiosk.xml'))
    assert prefs.find("boolean[@name='enabled']").attrib['value'] == 'false'
    assert shell('pm', 'path', 'ru.domovoy').startswith('package:')
    assert 'verified' in shell('locksettings', 'verify', '--old', args.pin).lower()


def finish():
    (args.output / 'result.json').write_text(json.dumps({'serial': args.serial, 'api': shell('getprop', 'ro.build.version.sdk'),
        'owner_removed': True, 'admin_removed': True, 'restrictions': 'none', 'lock_task': 'NONE',
        'home': baseline['home'], 'private_and_shared_data_retained': True, 'pin_retained': True,
        'reboot_verified': True}, indent=2) + '\n')


assert args.serial.startswith('emulator-') and shell('getprop', 'ro.kernel.qemu') == '1', 'Emulator only'
assert baseline['serial'] == args.serial and baseline['device_owner'] is None
assert shell('settings', 'get', 'global', 'airplane_mode_on') == '1', 'Use an offline test emulator'
assert shell('settings', 'get', 'global', 'wifi_on') == '0'
assert shell('settings', 'get', 'global', 'mobile_data') == '0'
if args.verify_after_reboot:
    for evidence in ['locked-device_policy.txt', 'cancelled-removal-device_policy.txt', 'unmanaged-settings-device_policy.txt']:
        assert (args.output / evidence).exists(), 'Run the full flow before resuming reboot checks'
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    if 'deviceLocked=1' in shell('dumpsys', 'trust'):
        shell('input', 'keyevent', 'KEYCODE_MENU')
        pin(args.pin)
    verify_unmanaged()
    snapshot('after-reboot')
    finish()
    print('PASS: unmanaged state and retained data verified after reboot', flush=True)
    raise SystemExit(0)
owner = shell('dpm', 'list-owners')
if owner == 'no owners':
    shell('dpm', 'set-device-owner', 'ru.domovoy/.kiosk.PanelDeviceAdmin')
    owner = shell('dpm', 'list-owners')
assert 'admin=ru.domovoy/.kiosk.PanelDeviceAdmin,DeviceOwner' in owner, owner
shell('run-as', 'ru.domovoy', 'sh', '-c', 'mkdir -p files && printf retained-private-data > files/owner-removal-proof')
shell('am', 'start', '-n', 'ru.domovoy/.MainActivity')
if lock_mode() != 'LOCKED':
    tap_label('Защита')
    tap_label('Включить защиту')
    pin(args.pin)
    wait_for(lambda: lock_mode() == 'LOCKED', 'real lock task activation')
snapshot('locked')
shell('am', 'start', '-a', 'android.settings.SETTINGS')
blocked = ui()
assert 'com.android.settings' not in {n.attrib.get('package') for n in blocked.iter('node')}, 'Settings must be blocked'
if any(n.attrib.get('text') == 'OK' for n in blocked.iter('node')):
    tap_label('OK')
print('PASS: PIN activation and Settings confinement', flush=True)

# A wrong credential and cancellation must preserve both owner and confinement.
tap_label('Защита')
tap_label('Снять управление планшетом')
pin('1357' if args.pin != '1357' else '8642')
assert 'com.android.systemui' in {n.attrib.get('package') for n in ui().iter('node')}
shell('input', 'keyevent', 'KEYCODE_BACK')
# The first Back can dismiss only the PIN keyboard; the next cancels the native prompt.
if 'com.android.systemui' in {n.attrib.get('package') for n in ui().iter('node')}:
    shell('input', 'keyevent', 'KEYCODE_BACK')
wait_for(lambda: 'ru.domovoy' in {n.attrib.get('package') for n in ui().iter('node')}, 'cancelled PIN returns to panel')
assert lock_mode() == 'LOCKED'
assert 'DeviceOwner' in shell('dpm', 'list-owners')
snapshot('cancelled-removal')
print('PASS: wrong PIN and cancellation preserve protection', flush=True)

tap_label('Защита')
tap_label('Снять управление планшетом')
pin(args.pin)
wait_for(lambda: shell('dpm', 'list-owners') == 'no owners', 'owner removal')
wait_for(lambda: not re.search(r'^\s*ru\.domovoy/\.kiosk\.PanelDeviceAdmin:', shell('dumpsys', 'device_policy'), re.M), 'asynchronous admin removal')
# Android can retain the old ordinary HOME role after managed policies are removed.
# The app opens Android's Home settings so the user can make this ordinary default choice.
snapshot('home-choice')
tap_label(args.home_label)
verify_unmanaged()
shell('am', 'start', '-a', 'android.settings.SETTINGS')
assert 'com.android.settings' in {n.attrib.get('package') for n in ui().iter('node')}
snapshot('unmanaged-settings')
shell('input', 'keyevent', 'KEYCODE_HOME')
assert 'com.google.android.apps.nexuslauncher' in {n.attrib.get('package') for n in ui().iter('node')}
shell('input', 'keyevent', 'KEYCODE_APP_SWITCH')
wait_for(lambda: any('overview' in n.attrib.get('resource-id', '') or n.attrib.get('text') in ('Screenshot', 'Select', 'Clear all') for n in ui().iter('node')), 'Recents available')
snapshot('unmanaged-recents')
print('PASS: owner/admin/policies gone; Settings, Home and Recents available; data/PIN retained', flush=True)

subprocess.check_call(adb + ['reboot'])
subprocess.run(adb + ['wait-for-device'], check=True, timeout=90)
wait_for(lambda: shell('getprop', 'sys.boot_completed') == '1', 'boot after owner removal', timeout=90)
shell('input', 'keyevent', 'KEYCODE_WAKEUP')
shell('input', 'keyevent', 'KEYCODE_MENU')
pin(args.pin)
wait_for(lambda: 'com.google.android.apps.nexuslauncher' in {n.attrib.get('package') for n in ui().iter('node')}, 'ordinary Home after reboot')
verify_unmanaged()
snapshot('after-reboot')
finish()
print('PASS: unmanaged state and retained data survive reboot', flush=True)
