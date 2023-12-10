/**********************************************************************
*
*  PRODUCT: Bestcode Utilities
*  COPYRIGHT:  (C) COPYRIGHT Suavi Ali Demir 2000-2010
*
*  The source code for this program is not GNU or is not free.
*  Source code is distributed with the binaries to improve
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
package com.bestcode.util;

/**
 * Abstract class that implements QuickSort.
 * To use, derive from it and override compareTo and swap methods.
 */
public abstract class QuickSort {

	/////////////////////////////////////////////////////////////////////////////
	// QuickSort algorithm
	public void sort(int L, int R){
    int i, j, p;
    do{
      i = L;
      j = R;
      p = (L + R) >> 1;
      do{
        while (compareTo(i, p) < 0) ++i;
        while (compareTo(j, p) > 0) --j;
        if (i <= j){
          swap(i, j);
          if (p==i){
            p = j;
          }else if (p==j){
            p = i;
          }
          ++i;
          --j;
        }
      }while(i <= j);
      if (L < j){
        sort(L, j);
      }
      L = i;
    }while (i < R);
	}

	/////////////////////////////////////////////////////////////////////////////
	// similar to String.compareTo(), return negative or positive when not equal and Zero when equal.
	//
	abstract public int compareTo(int i, int j);//must be overriden by derived classes.

	/////////////////////////////////////////////////////////////////////////////
	// swap i'th element with j'th element.
	//
	abstract public void swap(int i, int j);//must be overriden by derived classes.
}
