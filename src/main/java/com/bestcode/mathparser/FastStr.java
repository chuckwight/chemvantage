/**********************************************************************
*
*  PRODUCT: Bestcode Math Parser
*  COPYRIGHT:  (C) COPYRIGHT Suavi Ali Demir 2000-2015
*
*  The source code for this program is not GNU or is not free. 
*  Source code is distributed with the math parser binaries to improve
*  maintanability of the software and the user of the software is not
*  granted rights to modify and use source code to create a 
*  competitive product. 
*
*  The user is granted rights to modify and recompile source code 
*  to fix bugs/incompatibilities in the binaries which user has purchased.
*  
*  Resulting binaries cannot be re-sold as a competitive product but they 
*  can be used solely to replace the original binaries.
*  
*  Copyright and all rights of this software, irrespective of what 
*  has been deposited with the U.S. Copyright Office belongs 
*  to Suavi Ali Demir.
* 
***********************************************************************/
package com.bestcode.mathparser;

/**
 * A CharSequence to work around behavior change in String.substring which in Java 8 no longer
 * points to a range in the original char[] but makes a copy. This class will bring back the old
 * semantic that was cheaper and faster for parsing purposes. 
 * 
 * This is a special purpose class for the math parser only. Do not use it as a general string
 * replacement because the equals method is implemented to work with the math parser only.
 */
final class FastStr implements CharSequence {

	private final char[] value;
	private final int offset;
	private final int count;
	private Integer hashcode;
	private String str;
	
	public FastStr(char[] value, int offset, int count) {
		this.value = value;
		this.offset = offset;
		this.count = count;
	}
	
	public int length() {
		return count;
	}

	public char charAt(int index) {
		return value[offset + index];
	}

	public CharSequence subSequence(int start, int end) {
		return new FastStr(value, offset + start, end - start);
	}

	public FastStr substring(int start, int end) {
		return new FastStr(value, offset + start, end - start);
	}

	public int indexOf(char ch) {
		for (int i = offset, n = offset + count; i < n; i++) {
			if (value[i] == ch) {
				return i - offset;
			}
		}
		return -1;
	}
	
	public FastStr trimLeft(){
		if (value[offset] != ' ') {
			return this;
		}
		int left = offset;
		while (left < offset + count && value[left] == ' '){
			++left;
		};
		return new FastStr(value, left, count - (left - offset));
	}

	public FastStr trimRight(){
		if (value[offset + count - 1] != ' ') {
			return this;
		}
		int right = offset + count -1;
		while (right >= offset && value[right] == ' '){
			--right;
		};
		return new FastStr(value, offset, (right - offset) + 1);
	}

	public FastStr trim(){
		return trimLeft().trimRight();
	}
	
	@Override
	public int hashCode() {
		if (hashcode == null) {
			// We use String hashcode because this gets compared to String keys that are in variables
			// and functions hashmaps.
			hashcode = this.toString().hashCode();
		}
		return hashcode;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		// We compare against Strings in variables and functions hashmaps in the parser:
		if (obj instanceof String) {
			String other = (String) obj;
			if (other.length() != this.count) {
				return false;
			}
			for (int i = offset, n = offset + count; i < n; i++) {
				if (value[i] != other.charAt(i - offset)) {
					return false;
				}
			}
		}
		else if (obj instanceof FastStr) {
			FastStr other = (FastStr) obj;
			if (other.count != this.count) {
				return false;
			}
			for (int i = offset, n = offset + count; i < n; i++) {
				if (value[i] != other.value[other.offset + i]) {
					return false;
				}
			}
		} else {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		if (str == null) {
			str = new String(value, offset, count);
		}
		return str;
	}

}
