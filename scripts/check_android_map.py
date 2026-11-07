"""Contrôle de cohérence du tableau de carte embarqué, sans navigateur ni réseau."""
from __future__ import annotations
from pathlib import Path
import re
import subprocess
import tempfile

MAP = Path("app/src/main/assets/radar-map.html")
JAVA = Path("app/src/main/java/fr/franceclim/pro/MapActivity.java")


def main():
    html = MAP.read_text(encoding="utf-8")
    java = JAVA.read_text(encoding="utf-8")
    expected = (
        'id="map"', 'id="search"', 'id="counter"', 'id="refresh"',
        'data-filter="clim"', 'data-filter="eau"', 'data-filter="high"',
        'id="sheet"', 'id="fallback"', 'window.renderRadar=',
        'window.renderRadarError=', 'RadarNative.openLead',
    )
    missing = [v for v in expected if v not in html]
    if missing:
        raise SystemExit("Carte incomplète : " + ", ".join(missing))
    if "personName" in html or "callbackEvidence" in html:
        raise SystemExit("Risque : coordonnées privées directement intégrées dans la carte HTML")
    if "addJavascriptInterface(new MapBridge()" not in java:
        raise SystemExit("Pont vers fiches locales absent")
    if 'settings.setAllowUniversalAccessFromFileURLs(false)' not in java:
        raise SystemExit("Fichiers privés non protégés contre les accès externes")
    if "fallbackCenters()" not in java or "GEO_JSON" not in java:
        raise SystemExit("Cache ou centres de secours manquants")
    scripts = re.findall(r"<script(?:\s[^>]*)?>([\s\S]*?)</script>", html)
    scripts = [script for script in scripts if script.strip()]
    if len(scripts) != 1:
        raise SystemExit(f"Attendu un script applicatif inline, obtenu : {len(scripts)}")
    with tempfile.TemporaryDirectory() as td:
        script_file = Path(td) / "radar-map.js"
        script_file.write_text(scripts[0], encoding="utf-8")
        result = subprocess.run(
            ["node", "--check", str(script_file)],
            text=True, capture_output=True, check=False
        )
    if result.returncode:
        raise SystemExit("Javascript invalide : " + result.stderr)
    print("CARTE OK : JavaScript valide, filtres, recherche, fiches, cache, mode dégradé.")


if __name__ == "__main__":
    main()
