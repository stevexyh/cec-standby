"""Host-side profile validation. No subprocesses or device access."""
import json
import re

KEYS = {"schemaVersion", "id", "manufacturer", "model", "sdk", "firmware", "vendorId", "port", "inputId", "player", "action", "inputExtra", "inputValue", "launchers", "keepAdb", "enabled"}
COMPONENT = r"[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+/(?:\.)?[a-zA-Z][a-zA-Z0-9_.$]*"

def validate(data):
    if not isinstance(data, dict) or set(data) != KEYS:
        raise ValueError("Missing or unknown profile keys")
    for key, low, high in [("schemaVersion", 1, 1), ("sdk", 28, 28), ("vendorId", 0, 16777214), ("port", 1, 15), ("inputValue", 0, 65535)]:
        if type(data[key]) is not int or not low <= data[key] <= high:
            raise ValueError("Invalid " + key)
    patterns = {"id": r"[a-z0-9][a-z0-9-]{0,63}", "manufacturer": r"[A-Za-z0-9_. -]{1,80}", "model": r"[A-Za-z0-9_. -]{1,80}", "firmware": r"[A-Za-z0-9_. -]{1,120}", "inputId": r"[A-Za-z0-9_.$/]{1,240}", "player": COMPONENT, "action": r"[A-Za-z][A-Za-z0-9_.]{1,180}", "inputExtra": r"[A-Za-z][A-Za-z0-9_]{0,63}"}
    for key, pattern in patterns.items():
        if not isinstance(data[key], str) or not re.fullmatch(pattern, data[key]):
            raise ValueError("Invalid " + key)
    launchers = data["launchers"]
    if not isinstance(launchers, list) or not 1 <= len(launchers) <= 16 or any(not isinstance(x, str) or not re.fullmatch(COMPONENT, x) for x in launchers):
        raise ValueError("Invalid launchers")
    for key in ("keepAdb", "enabled"):
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
