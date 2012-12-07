import java.util.Map;


public class Input {
	public int largestOut;
	
	public Map<String, Range> variableLives;
	
	public boolean isLeaf;

	/**
	 * @param largestOut
	 * @param variableLives
	 */
	public Input(int largestOut, Map<String, Range> variableLives) {
		super();
		this.largestOut = largestOut;
		this.variableLives = variableLives;
		this.isLeaf = true;
	}
}
