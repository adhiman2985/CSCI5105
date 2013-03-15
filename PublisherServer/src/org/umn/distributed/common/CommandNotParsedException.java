package org.umn.distributed.common;

public class CommandNotParsedException extends Exception {

	public CommandNotParsedException(String error, String usage) {
		super(error + "\t" + usage);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

}
