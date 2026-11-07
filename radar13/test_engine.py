import unittest
from pathlib import Path

from radar13.engine import CITIES, classify
from radar13.run import parse_feed


class RadarTests(unittest.TestCase):
    def test_all_communes(self):
        self.assertEqual(len(CITIES), 119)

    def test_urgent_water_heater(self):
        result = classify("Je cherche un plombier à Marseille pour remplacer mon cumulus HS. Plus d'eau chaude.")
        self.assertEqual(result["category"], "Chauffe-eau")
        self.assertEqual(result["city"], "Marseille")
        self.assertTrue(result["urgency"])

    def test_clim_martigues(self):
        result = classify("Je cherche un artisan pour faire installer une climatisation multisplit dans ma maison à Martigues.")
        self.assertEqual(result["category"], "Climatisation")
        self.assertEqual(result["city"], "Martigues")

    def test_no_professional_ad(self):
        self.assertIsNone(classify("Entreprise spécialisée nous proposons installation climatisation Marseille. Contactez nous."))

    def test_no_used_goods(self):
        self.assertIsNone(classify("Je vends mon climatiseur d'occasion à Istres, je cherche un acheteur."))

    def test_out_of_area(self):
        self.assertIsNone(classify("Je cherche un installateur pour ma climatisation à Toulon 83000."))

    def test_demo_feed(self):
        raw = Path("fixtures/demo.rss").read_bytes()
        matches = [classify(entry["text"]) for entry in parse_feed(raw)]
        self.assertEqual(sum(m is not None for m in matches), 2)


if __name__ == "__main__":
    unittest.main()
