package util;

public class GradientList {

	private Gradient[] list = new Gradient[6];

	public GradientList() {
		list[0] = new Gradient("#ff8415", "#ff8d22");
		list[2] = new Gradient("#ff1fcd", "#cc12a3");
		list[3] = new Gradient("#3210c3", "#2b119c");
		list[1] = new Gradient("#525af4", "#434ae0");
		list[4] = new Gradient("#3de95a", "#31cb4b");
		list[5] = new Gradient("#e5d12c", "#d7c424");
	}

	/**
	 * @returns an gradient color, associated with the index.
	 */
	public Gradient getGradient(int index) {
		return list[index];
	}

	/**
	 * @return basic sendcolor gradient
	 */
	public Gradient sendColor() {
		return new Gradient("#f22d2d", "#d10c0c");
	}

	public int getSize() {
		return list.length;
	}

	public class Gradient {
		public String color1;
		public String color2;

		public Gradient(String inputColor1, String inputColor2) {
			color1 = inputColor1;
			color2 = inputColor2;
		}
	}
}
