package org.gsanson.frozenbubble;

public interface Opponent {

	/**
	 * Checks whether opponent has a control command awaiting
	 * @return
	 */
	public boolean isComputing();

	/**
	 * Get the exact direction (radian value) pointer should reach
	 * @param currentDirection
	 * @return
	 */
	public double getExactDirection(double currentDirection);

	/**
	 * The action opponent want to make (left, right, fire)
	 * @param currentDirection
	 * @return
	 */
	public int getAction(double currentDirection);

	/**
	 * Make any necessary computation before next turn
	 * @param currentColor
	 * @param nextColor
	 * @param compressor
	 */
	public void compute(int currentColor, int nextColor, int compressor);

	public int[] getBallDestination();
}
