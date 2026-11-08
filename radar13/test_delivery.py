"""Regression tests: no real customers or network calls are used."""
import email
from email.message import EmailMessage
import unittest
from unittest.mock import patch

from radar13.run import fingerprint, scan_bluesky, scan_feeds, alert_mailbox, alert


REQUEST = "Je cherche un plombier pour remplacer mon cumulus à Marseille, plus d'eau chaude."
FEED = "https://example.org/authorized.rss"


class DeliveryRegressionTests(unittest.TestCase):
    def test_rss_unconfigured_channel_does_not_consume_lead(self):
        state = {"feeds": {fingerprint(FEED): []}}
        entry = {"id": "post-1", "text": REQUEST, "title": "", "link": ""}
        with patch("radar13.run.load_feeds", return_value=[("Flux test", FEED)]), \
             patch("radar13.run.fetch_feed", return_value=[entry]), \
             patch("radar13.run.whatsapp_configured", return_value=False):
            self.assertEqual(scan_feeds(state), (1, 0))
            self.assertEqual(state["feeds"][fingerprint(FEED)], [])
        with patch("radar13.run.load_feeds", return_value=[("Flux test", FEED)]), \
             patch("radar13.run.fetch_feed", return_value=[entry]), \
             patch("radar13.run.alert", return_value=True) as send:
            self.assertEqual(scan_feeds(state), (1, 1))
            send.assert_called_once()
            self.assertEqual(scan_feeds(state), (0, 0))

    def test_bluesky_unconfigured_channel_keeps_qualified_lead(self):
        state = {"feeds": {}, "bluesky": []}
        entry = {"id": "at://example/post/one", "text": REQUEST, "link": ""}
        with patch("radar13.run.fetch_search", return_value=[entry]), \
             patch("radar13.run.whatsapp_configured", return_value=False):
            self.assertEqual(scan_bluesky(state), (1, 0))
            self.assertEqual(state["bluesky"], [])
        with patch("radar13.run.fetch_search", return_value=[entry]), \
             patch("radar13.run.alert", return_value=True) as send:
            self.assertEqual(scan_bluesky(state), (1, 1))
            send.assert_called_once()
            self.assertEqual(scan_bluesky(state), (0, 0))

    def test_fingerprint_retention_preserves_latest_acknowledged(self):
        previous = [f"older-{i:04d}" for i in range(4000)]
        state = {"feeds": {fingerprint(FEED): previous[:]}}
        entry = {"id": "newest", "text": REQUEST, "title": "", "link": ""}
        with patch("radar13.run.load_feeds", return_value=[("Flux test", FEED)]), \
             patch("radar13.run.fetch_feed", return_value=[entry]), \
             patch("radar13.run.alert", return_value=True):
            self.assertEqual(scan_feeds(state), (1, 1))
        remaining = state["feeds"][fingerprint(FEED)]
        self.assertEqual(len(remaining), 4000)
        self.assertNotIn("older-0000", remaining)
        self.assertEqual(remaining[-1], fingerprint("newest"))

    def test_bluesky_retention_preserves_latest_acknowledged(self):
        previous = [f"older-{i:04d}" for i in range(4000)]
        state = {"feeds": {}, "bluesky": previous[:]}
        entry = {"id": "at://example/post/newest", "text": REQUEST, "link": ""}
        with patch("radar13.run.fetch_search", return_value=[entry]), \
             patch("radar13.run.alert", return_value=True):
            self.assertEqual(scan_bluesky(state), (1, 1))
        self.assertEqual(len(state["bluesky"]), 4000)
        self.assertNotIn("older-0000", state["bluesky"])
        self.assertEqual(state["bluesky"][-1], fingerprint(entry["id"]))

    def test_provider_message_id_required_to_mark_as_delivered(self):
        candidate = {"id": "id", "text": REQUEST, "link": ""}
        with patch("radar13.run.whatsapp_configured", return_value=True), \
             patch("radar13.run.send_template", return_value=None):
            self.assertFalse(alert(candidate, "Source autorisée"))
        with patch("radar13.run.whatsapp_configured", return_value=True), \
             patch("radar13.run.send_template", return_value="wamid.test-id"):
            self.assertTrue(alert(candidate, "Source autorisée"))

    def test_failed_delivery_can_be_retried(self):
        state = {"feeds": {fingerprint(FEED): []}}
        entry = {"id": "post-fail", "text": REQUEST, "title": "", "link": ""}
        with patch("radar13.run.load_feeds", return_value=[("Flux test", FEED)]), \
             patch("radar13.run.fetch_feed", return_value=[entry]), \
             patch("radar13.run.alert", return_value=False):
            self.assertEqual(scan_feeds(state), (1, 0))
            self.assertEqual(state["feeds"][fingerprint(FEED)], [])
        with patch("radar13.run.load_feeds", return_value=[("Flux test", FEED)]), \
             patch("radar13.run.fetch_feed", return_value=[entry]), \
             patch("radar13.run.alert", return_value=True):
            self.assertEqual(scan_feeds(state), (1, 1))

    def test_mailbox_keeps_undelivered_lead_unread(self):
        msg = EmailMessage()
        msg["Subject"] = "Remplacement chauffe-eau Marseille"
        msg.set_content(REQUEST)

        class Inbox:
            def __init__(self):
                self.marked = []
            def __enter__(self):
                return self
            def __exit__(self, *args):
                return False
            def login(self, *args):
                pass
            def select(self, *args):
                return ("OK", [])
            def search(self, *args):
                return ("OK", [b"42"])
            def fetch(self, *args):
                return ("OK", [(b"42", msg.as_bytes())])
            def store(self, *args):
                self.marked.append(args)

        box = Inbox()
        env = {"RADAR_IMAP_HOST": "mail.example.org",
               "RADAR_IMAP_USER": "test@example.org",
               "RADAR_IMAP_PASSWORD": "fake-test-password"}
        with patch.dict("os.environ", env), \
             patch("radar13.run.imaplib.IMAP4_SSL", return_value=box), \
             patch("radar13.run.alert", return_value=False):
            self.assertEqual(alert_mailbox(), 0)
            self.assertEqual(box.marked, [])
        with patch.dict("os.environ", env), \
             patch("radar13.run.imaplib.IMAP4_SSL", return_value=box), \
             patch("radar13.run.alert", return_value=True):
            self.assertEqual(alert_mailbox(), 1)
            self.assertEqual(len(box.marked), 1)


if __name__ == "__main__":
    unittest.main()
