import unittest
from unittest.mock import patch
from radar13.whatsapp import configured, template_payload, send_template


class WhatsAppTests(unittest.TestCase):
    def setUp(self):
        self.env = {
            "RADAR_WHATSAPP_ACCESS_TOKEN": "fake-token-not-used",
            "RADAR_WHATSAPP_PHONE_NUMBER_ID": "1234567890",
            "RADAR_WHATSAPP_RECIPIENT": "+33 6 12 34 56 78",
            "RADAR_WHATSAPP_TEMPLATE_NAME": "france_clim_radar_alerte",
            "RADAR_WHATSAPP_TEMPLATE_LANG": "fr",
        }

    def test_no_credentials_means_no_automatic_alert(self):
        self.assertFalse(configured({}))
        self.assertTrue(configured(self.env))

    def test_template_sends_only_four_safe_fields(self):
        payload = template_payload("Chauffe-eau", "Marseille", 92, "Flux public", self.env)
        self.assertEqual(payload["messaging_product"], "whatsapp")
        self.assertEqual(payload["type"], "template")
        self.assertEqual(payload["to"], "33612345678")
        fields = payload["template"]["components"][0]["parameters"]
        self.assertEqual(len(fields), 4)
        self.assertEqual(fields[0]["text"], "Chauffe-eau")
        self.assertEqual(fields[2]["text"], "92")
        self.assertNotIn("fake-token-not-used", str(payload))

    def test_invalid_template_rejected(self):
        self.env["RADAR_WHATSAPP_TEMPLATE_NAME"] = "not valid"
        with self.assertRaises(ValueError):
            template_payload("Climatisation", "Arles", 80, "Source", self.env)

    def test_no_send_when_not_configured(self):
        with patch.dict("os.environ", {}, clear=True):
            with self.assertRaises(RuntimeError):
                send_template("Climatisation", "Arles", 95, "Flux RSS")


if __name__ == "__main__":
    unittest.main()
