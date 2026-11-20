"""Host-side profile validation. No subprocesses or device access."""
import json
import re

KEYS = {"schemaVersion", "id", "manufacturer", "model", "sdk", "firmware", "vendorId", "port", "inputId", "player", "action", "inputExtra", "inputValue", "launchers", "keepAdb", "enabled"}
# Schema 2 adds firmware adapters; wake-handoff keys are present only when the handoff is enabled.
HANDOFF_KEYS = {"action", "inputExtra", "inputValue", "launchers"}
V2_KEYS = {"events", "inputSource", "standbyKey", "wakeHandoff"}
CHOICES = {"events": ("xiaomi", "tcl"), "inputSource": ("setting", "tv-input-session"), "standbyKey": ("POWER", "SLEEP")}
COMPONENT = r"[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+/(?:\.)?[a-zA-Z][a-zA-Z0-9_.$]*"

def validate(data):
    if not isinstance(data, dict) or type(data.get("schemaVersion")) is not int or data["schemaVersion"] not in (1, 2):
        raise ValueError("Invalid schemaVersion")
    if data["schemaVersion"] == 1:
        expected, handoff = KEYS, True
    else:
        handoff = data.get("wakeHandoff") is True
        expected = (KEYS | V2_KEYS) - (set() if handoff else HANDOFF_KEYS)
    if set(data) != expected:
        raise ValueError("Missing or unknown profile keys")
    ranges = [("sdk", 28, 28), ("vendorId", 0, 16777214), ("port", 1, 15)] + ([("inputValue", 0, 65535)] if handoff else [])
    for key, low, high in ranges:
        if type(data[key]) is not int or not low <= data[key] <= high:
            raise ValueError("Invalid " + key)
    patterns = {"id": r"[a-z0-9][a-z0-9-]{0,63}", "manufacturer": r"[A-Za-z0-9_. -]{1,80}", "model": r"[A-Za-z0-9_. -]{1,80}", "firmware": r"[A-Za-z0-9_. -]{1,120}", "inputId": r"[A-Za-z0-9_.$/]{1,240}", "player": COMPONENT}
    if handoff:
        patterns.update({"action": r"[A-Za-z][A-Za-z0-9_.]{1,180}", "inputExtra": r"[A-Za-z][A-Za-z0-9_]{0,63}"})
    for key, pattern in patterns.items():
        if not isinstance(data[key], str) or not re.fullmatch(pattern, data[key]):
            raise ValueError("Invalid " + key)
    if handoff:
        launchers = data["launchers"]
        if not isinstance(launchers, list) or not 1 <= len(launchers) <= 16 or any(not isinstance(x, str) or not re.fullmatch(COMPONENT, x) for x in launchers):
            raise ValueError("Invalid launchers")
    if data["schemaVersion"] == 2:
        for key, allowed in CHOICES.items():
            if data[key] not in allowed:
                raise ValueError("Invalid " + key)
    for key in ("keepAdb", "enabled") + (("wakeHandoff",) if data["schemaVersion"] == 2 else ()):
        if type(data[key]) is not bool:
            raise ValueError("Invalid " + key)
    return data

def load(path):
    raw = path.read_text()
    if len(raw) > 16384:
        raise ValueError("Profile too large")
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("Duplicate key: " + key)
            result[key] = value
        return result
    return validate(json.loads(raw, object_pairs_hook=unique))
