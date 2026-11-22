#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""云函数核心逻辑单元测试：python3 -m unittest test_app -v"""

import json
import threading
import time
import unittest
import urllib.error
import urllib.request
from unittest import mock

import app as proxy


class StripDataUriTest(unittest.TestCase):
    def test_plain_base64_kept(self):
        self.assertEqual(proxy.strip_data_uri("SGVsbG8="), "SGVsbG8=")

    def test_data_uri_prefix_removed(self):
        self.assertEqual(
            proxy.strip_data_uri("data:image/jpeg;base64,abc123"), "abc123"
        )

    def test_non_string_returns_empty(self):
        self.assertEqual(proxy.strip_data_uri(None), "")
        self.assertEqual(proxy.strip_data_uri(123), "")


class ToIntTest(unittest.TestCase):
    def test_plain_int(self):
        self.assertEqual(proxy._to_int(200), 200)

    def test_float_truncated(self):
        self.assertEqual(proxy._to_int(150.6), 150)

    def test_numeric_string_with_unit(self):
        self.assertEqual(proxy._to_int("200g"), 200)
        self.assertEqual(proxy._to_int("约150克"), 150)

    def test_invalid_returns_none(self):
        self.assertIsNone(proxy._to_int("abc"))
        self.assertIsNone(proxy._to_int(None))
        self.assertIsNone(proxy._to_int(True))


class ParseFoodsTest(unittest.TestCase):
    def test_normal_array(self):
        text = '[{"name":"白米饭","grams":200,"calories":232}]'
        self.assertEqual(
            proxy.parse_foods(text),
            [{"name": "白米饭", "grams": 200, "calories": 232}],
        )

    def test_markdown_wrapped_json(self):
        text = '好的：\n```json\n[{"name":"鸡蛋","grams":50,"calories":72}]\n```'
        self.assertEqual(len(proxy.parse_foods(text)), 1)

    def test_empty_array(self):
        self.assertEqual(proxy.parse_foods("[]"), [])

    def test_no_brackets_returns_empty(self):
        self.assertEqual(proxy.parse_foods("图片里没有食物"), [])

    def test_bad_item_skipped_not_failing(self):
        text = '[{"name":"米饭","grams":"200g","calories":"232"},{"name":"","grams":1,"calories":1},{"name":"坏数据","grams":"很多","calories":10}]'
        foods = proxy.parse_foods(text)
        self.assertEqual(len(foods), 1)
        self.assertEqual(foods[0]["name"], "米饭")
        self.assertEqual(foods[0]["grams"], 200)

    def test_values_clamped(self):
        text = '[{"name":"巨无霸","grams":999999,"calories":-5}]'
        foods = proxy.parse_foods(text)
        self.assertEqual(foods[0]["grams"], proxy.MAX_GRAMS)
        self.assertEqual(foods[0]["calories"], 0)

    def test_invalid_json_raises(self):
        with self.assertRaises(proxy.ModelOutputError):
            proxy.parse_foods("[{name: 米饭]")


class EasterEggTest(unittest.TestCase):
    def test_easter_item_passthrough(self):
        text = '[{"easterEgg":true,"name":"核弹","grams":0,"calories":0}]'
        self.assertEqual(
            proxy.parse_foods(text),
            [{"name": "核弹", "grams": 0, "calories": 0, "easterEgg": True}],
        )

    def test_easter_values_forced_zero(self):
        text = '[{"easterEgg":true,"name":"汽油桶","grams":300,"calories":9999}]'
        foods = proxy.parse_foods(text)
        self.assertEqual(foods[0]["grams"], 0)
        self.assertEqual(foods[0]["calories"], 0)
        self.assertTrue(foods[0]["easterEgg"])

    def test_easter_without_numbers_kept(self):
        text = '[{"easterEgg":true,"name":"燃气罐"}]'
        foods = proxy.parse_foods(text)
        self.assertEqual(len(foods), 1)
        self.assertTrue(foods[0]["easterEgg"])

    def test_easter_requires_name(self):
        text = '[{"easterEgg":true,"name":"  ","grams":0,"calories":0}]'
        self.assertEqual(proxy.parse_foods(text), [])

    def test_normal_food_has_no_easter_flag(self):
        foods = proxy.parse_foods('[{"name":"白米饭","grams":200,"calories":232}]')
        self.assertNotIn("easterEgg", foods[0])


class AppKeyTest(unittest.TestCase):
    def tearDown(self):
        proxy.EXPECTED_APP_KEY = ""

    def test_fail_closed_when_not_configured(self):
        proxy.EXPECTED_APP_KEY = ""
        self.assertFalse(proxy.check_app_key(""))
        self.assertFalse(proxy.check_app_key("anything"))

    def test_match_required(self):
        proxy.EXPECTED_APP_KEY = "secret-key"
        self.assertTrue(proxy.check_app_key("secret-key"))
        self.assertFalse(proxy.check_app_key("wrong"))
        self.assertFalse(proxy.check_app_key(None))


class RateLimiterTest(unittest.TestCase):
    def test_per_minute_limit(self):
        limiter = proxy.RateLimiter(per_minute=2, per_day=100, global_per_day=0)
        self.assertTrue(limiter.check("1.1.1.1")[0])
        self.assertTrue(limiter.check("1.1.1.1")[0])
        ok, msg = limiter.check("1.1.1.1")
        self.assertFalse(ok)
        self.assertIn("频繁", msg)

    def test_per_ip_isolation(self):
        limiter = proxy.RateLimiter(per_minute=1, per_day=100, global_per_day=0)
        self.assertTrue(limiter.check("1.1.1.1")[0])
        self.assertTrue(limiter.check("2.2.2.2")[0])

    def test_per_day_limit(self):
        limiter = proxy.RateLimiter(per_minute=100, per_day=2, global_per_day=0)
        self.assertTrue(limiter.check("1.1.1.1")[0])
        self.assertTrue(limiter.check("1.1.1.1")[0])
        ok, msg = limiter.check("1.1.1.1")
        self.assertFalse(ok)
        self.assertIn("上限", msg)

    def test_global_day_quota(self):
        limiter = proxy.RateLimiter(per_minute=100, per_day=100, global_per_day=2)
        self.assertTrue(limiter.check("1.1.1.1")[0])
        self.assertTrue(limiter.check("2.2.2.2")[0])
        ok, msg = limiter.check("3.3.3.3")
        self.assertFalse(ok)
        self.assertIn("总次数", msg)

    def test_expired_windows_purged(self):
        limiter = proxy.RateLimiter(
            per_minute=10, per_day=10, global_per_day=0, cleanup_threshold=2
        )
        old = time.time() - 90000
        limiter.minute_windows = {"old-%d" % i: [old] for i in range(5)}
        limiter.day_windows = {"old-%d" % i: [old] for i in range(5)}
        self.assertTrue(limiter.check("9.9.9.9")[0])
        self.assertNotIn("old-0", limiter.minute_windows)
        self.assertNotIn("old-0", limiter.day_windows)


class BearerTokenTest(unittest.TestCase):
    def setUp(self):
        self.old_url = proxy.SUPABASE_URL
        self.old_key = proxy.SUPABASE_PUBLISHABLE_KEY
        proxy.SUPABASE_URL = "https://project.supabase.co"
        proxy.SUPABASE_PUBLISHABLE_KEY = "publishable-test"

    def tearDown(self):
        proxy.SUPABASE_URL = self.old_url
        proxy.SUPABASE_PUBLISHABLE_KEY = self.old_key

    def test_bearer_header_extracted(self):
        self.assertEqual(proxy.bearer_token({"Authorization": "Bearer abc"}), "abc")
        self.assertEqual(proxy.bearer_token({"Authorization": "bearer abc"}), "abc")
        self.assertEqual(proxy.bearer_token({"Authorization": "Basic abc"}), "")
        self.assertEqual(proxy.bearer_token({}), "")

    def test_valid_token_returns_user_id(self):
        class FakeResponse:
            def __enter__(self):
                return self

            def __exit__(self, *args):
                return False

            def read(self):
                return b'{"id":"user-1"}'

        with mock.patch.object(proxy.urllib.request, "urlopen", return_value=FakeResponse()) as call:
            self.assertEqual(proxy.verify_bearer_token("token-1"), "user-1")
        request = call.call_args.args[0]
        self.assertEqual(request.get_header("Authorization"), "Bearer token-1")
        self.assertTrue(request.full_url.endswith("/auth/v1/user"))

    def test_invalid_token_rejected(self):
        error = urllib.error.HTTPError("https://project.supabase.co", 401, "no", None, None)
        with mock.patch.object(proxy.urllib.request, "urlopen", side_effect=error):
            with self.assertRaises(proxy.AuthRejected):
                proxy.verify_bearer_token("bad-token")

    def test_missing_configuration_fails_closed(self):
        proxy.SUPABASE_URL = ""
        with self.assertRaises(proxy.AuthUnavailable):
            proxy.verify_bearer_token("token-1")

    def test_auth_timeout_fails_closed(self):
        with mock.patch.object(proxy.urllib.request, "urlopen", side_effect=TimeoutError()):
            with self.assertRaises(proxy.AuthUnavailable):
                proxy.verify_bearer_token("token-1")


class UnauthorizedRequestTest(unittest.TestCase):
    def test_unauthorized_request_never_calls_model(self):
        server = proxy.ThreadingHTTPServer(("127.0.0.1", 0), proxy.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        old_key = proxy.EXPECTED_APP_KEY
        proxy.EXPECTED_APP_KEY = "test-app-key"
        try:
            body = json.dumps({"appKey": "test-app-key", "image": "YWJj"}).encode("utf-8")
            request = urllib.request.Request(
                "http://127.0.0.1:%d/recognize" % server.server_address[1],
                data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with mock.patch.object(proxy, "call_deepseek") as model:
                with self.assertRaises(urllib.error.HTTPError) as caught:
                    urllib.request.urlopen(request, timeout=5)
                self.assertEqual(caught.exception.code, 401)
                model.assert_not_called()
        finally:
            proxy.EXPECTED_APP_KEY = old_key
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


class TextParsingTest(unittest.TestCase):
    def test_structured_food_result(self):
        result = proxy.parse_text_result(
            '{"intent":"add","dateOffset":-1,"mealType":1,'
            '"foods":[{"name":"米饭","grams":200,"calories":232}],'
            '"directCalories":null,"removeNames":[],"unmatched":[]}'
        )
        self.assertEqual(result["intent"], "add")
        self.assertEqual(result["dateOffset"], -1)
        self.assertEqual(result["mealType"], 1)
        self.assertEqual(result["foods"][0]["name"], "米饭")
        self.assertEqual(result["foods"][0]["grams"], 200)

    def test_invalid_json_rejected(self):
        with self.assertRaises(proxy.ModelOutputError):
            proxy.parse_text_result("not-json")

    def test_values_and_intent_are_controlled(self):
        result = proxy.parse_text_result(
            '{"intent":"other","dateOffset":99,"mealType":8,'
            '"directCalories":999999,"foods":"bad","removeNames":null,"unmatched":null}'
        )
        self.assertEqual(result["intent"], "unknown")
        self.assertEqual(result["dateOffset"], 30)
        self.assertIsNone(result["mealType"])
        self.assertEqual(result["directCalories"], proxy.MAX_CALORIES)
        self.assertEqual(result["foods"], [])


class TextEndpointAuthTest(unittest.TestCase):
    def test_text_endpoint_rejects_unauthorized_before_model(self):
        server = proxy.ThreadingHTTPServer(("127.0.0.1", 0), proxy.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        old_key = proxy.EXPECTED_APP_KEY
        proxy.EXPECTED_APP_KEY = "test-app-key"
        try:
            body = json.dumps({"appKey": "test-app-key", "text": "一碗米饭"}).encode("utf-8")
            request = urllib.request.Request(
                "http://127.0.0.1:%d/parse-food" % server.server_address[1],
                data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with mock.patch.object(proxy, "call_deepseek_text") as model:
                with self.assertRaises(urllib.error.HTTPError) as caught:
                    urllib.request.urlopen(request, timeout=5)
                self.assertEqual(caught.exception.code, 401)
                model.assert_not_called()
        finally:
            proxy.EXPECTED_APP_KEY = old_key
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
