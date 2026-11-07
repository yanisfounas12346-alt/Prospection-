import unittest
from unittest.mock import patch

from radar13.bluesky import parse_search
from radar13.run import scan_bluesky


class BlueskyTests(unittest.TestCase):
    @staticmethod
    def sample(rkey="3testabcdef"):
        return {
            "uri": f"at://did:plc:example/app.bsky.feed.post/{rkey}",
            "record": {"text": "Je cherche un artisan pour faire installer une climatisation dans ma maison à Marseille."},
            "author": {"handle": "compte-test.bsky.social"}
        }

    def test_official_json_to_source_link(self):
        posts = parse_search({"posts": [self.sample()]})
        self.assertEqual(len(posts), 1)
        self.assertEqual(posts[0]["link"],
            "https://bsky.app/profile/compte-test.bsky.social/post/3testabcdef")
        self.assertIn("Marseille", posts[0]["text"])
        self.assertNotIn("author", posts[0])

    def test_does_not_trust_invalid_handles(self):
        invalid = self.sample()
        invalid["author"]["handle"] = "https://evil.example/"
        self.assertEqual(parse_search({"posts": [invalid]}), [])

    def test_first_sync_suppresses_existing_alerts(self):
        state = {"feeds": {}}
        new = parse_search({"posts":[self.sample()]})
        with patch("radar13.run.fetch_search", return_value=new),              patch("radar13.run.alert") as sender:
            fresh, sent = scan_bluesky(state)
        self.assertEqual(fresh, 1)
        self.assertEqual(sent, 0)
        sender.assert_not_called()

    def test_later_new_post_alerts_once(self):
        state = {"feeds": {}, "bluesky":[]}
        data = parse_search({"posts": [self.sample("3newabcdef")]})
        with patch("radar13.run.fetch_search", return_value=data),              patch("radar13.run.alert", return_value=True) as sender:
            fresh, sent = scan_bluesky(state)
        self.assertEqual(fresh, 1)
        self.assertEqual(sent, 1)
        sender.assert_called_once()
        with patch("radar13.run.fetch_search", return_value=data),              patch("radar13.run.alert") as sender2:
            self.assertEqual(scan_bluesky(state), (0,0))
        sender2.assert_not_called()


if __name__ == "__main__":
    unittest.main()
