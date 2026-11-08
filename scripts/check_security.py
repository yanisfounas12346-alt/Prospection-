"""Static regressions for local CRM privacy and loss-free direct lead import.

These checks complement, but do not replace, Android device encryption tests.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STORE = (ROOT / "app/src/main/java/fr/franceclim/pro/RadarStore.java").read_text(encoding="utf-8")
SECURE = (ROOT / "app/src/main/java/fr/franceclim/pro/SecureLeadStore.java").read_text(encoding="utf-8")
CSV = (ROOT / "app/src/main/java/fr/franceclim/pro/CsvIntake.java").read_text(encoding="utf-8")
RSS = (ROOT / "app/src/main/java/fr/franceclim/pro/RadarScanner.java").read_text(encoding="utf-8")

assert 'putString("leads"' not in STORE, "CRM must not write customer records in plaintext"
assert 'getString("leads"' not in STORE, "CRM must read records through secure store"
assert 'AES/GCM/NoPadding' in SECURE, "Authenticated encryption missing"
assert 'AndroidKeyStore' in SECURE, "Key material must be in Android Keystore"
assert 'remove(LEGACY).commit()' in SECURE, "Legacy plaintext migration is not committed"
assert 'SecureLeadStore.save(' in STORE, "CRM encryption writes not wired"
assert 'addWithIdentity(' in CSV, "Different CSV customers may be deduplicated"
assert 'setContactByFingerprint(' in CSV, "Contact attachment must not depend on list index"
assert 'RadarStore.leads(ctx).length()-1' not in CSV, "Race in CSV contact assignment"
assert 'if("Déjà détectée".equals(added.reason))seen.add(key);' in RSS, (
    "RSS must not mark failed CRM saves as already seen"
)
print("SECURITY OK : encrypted CRM, identity-aware dedup, stable contacts, RSS retry.")
