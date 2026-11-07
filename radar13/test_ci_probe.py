import unittest
from radar13.engine import classify


class CIProbe(unittest.TestCase):
    def test_real_intent_excludes_sale(self):
        self.assertIsNotNone(classify("Je cherche un artisan pour faire remplacer mon chauffe eau en panne à Istres 13800."))
        self.assertIsNone(classify("Je vends mon chauffe eau à Marseille 13002."))


if __name__ == "__main__":
    unittest.main()
