package me.flame.turboscanner.test;

public final class JsonException extends RuntimeException {
    public JsonException(String msg) { super(msg); }

    public static JsonException invalidUtf8() { return new JsonException("Invalid UTF-8"); }

    public static JsonException unterminatedString() { return new JsonException("Unterminated string"); }

    public static JsonException unescapedControl() { return new JsonException("Unescaped control character"); }

    public static JsonException truncated() {
        return new JsonException("The read bytes is lower than expected.");
    }

    public static JsonException unbalancedBrackets(int pos) {
        return new JsonException("Unbalanced brackets at " + pos);
    }

    public static JsonException invalidNumber(int pos) {
        return new JsonException("Invalid number at " + pos);
    }

    public static JsonException unexpectedEnd(int pos) {
        return new JsonException("Unexpected end of input at " + pos);
    }
}
