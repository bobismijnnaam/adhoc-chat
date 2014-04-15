package util;

public class Util {
	public static byte[] append(byte[] original, byte[] addendum) {
		byte[] result = new byte[original.length + addendum.length];

		System.arraycopy(original, 0, result, 0, original.length);
		System.arraycopy(addendum, 0, result, original.length, addendum.length);

		return result;
	}

	public static String toHex(byte[] data) {
		String result = "";

		for (int i = 0; i < data.length; i++) {
			result += Integer.toHexString(data[i]);
		}

		return result;
	}

	public static String makeHtmlSafe(String oldStr) {
		return oldStr.replace("<", "&#60;").replace(">", "&#62;");
	}

	public static void main(String[] args) {
		System.out.println(Util.makeHtmlSafe("<<<<<<script>"));
	}
}
