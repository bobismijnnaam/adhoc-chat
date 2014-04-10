package util;


public class GradientList {

	private Gradient[] list = new Gradient[8];
	
	public GradientList() {
		list[0] = new Gradient("#000000", "#000000");
		list[1] = new Gradient("#000000", "#000000");
		list[2] = new Gradient("#000000", "#000000");
		list[3] = new Gradient("#000000", "#000000");
		list[4] = new Gradient("#000000", "#000000");
		list[5] = new Gradient("#000000", "#000000");
		list[6] = new Gradient("#000000", "#000000");
		list[7] = new Gradient("#000000", "#000000");
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

