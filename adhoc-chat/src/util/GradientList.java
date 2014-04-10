package util;


public class GradientList {

	private Gradient[] list = new Gradient[4];
	
	public GradientList() {
		list[0] = new Gradient("#ff8415", "#ff8d22");
		list[2] = new Gradient("#ff1fcd", "#cc12a3");
		list[3] = new Gradient("#3210c3", "#2b119c");
		list[1] = new Gradient("#525af4", "#434ae0");
	}
	
	/**
	 * @returns an gradient color, associated with the index.
	 */
	public Gradient getGradient(int index) {
		return list[0];
	}
	
	/**
	 * Contains two colors.
	 */
	public class Gradient {
		public String color1;
		public String color2;
		
		public Gradient(String inputColor1, String inputColor2) {
			color1 = inputColor1;
			color2 = inputColor2;
		}
	}
}

