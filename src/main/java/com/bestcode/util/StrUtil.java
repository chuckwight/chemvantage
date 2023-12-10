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

import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class	StrUtil{
  ResourceBundle res;
  
  public StrUtil(ResourceBundle bundle) {
    res = bundle;
  }

  public String getMessage(String key){
    try{
      String s = res.getString(key);
      if (s==null){ s = key; }
      return s;
    }catch(MissingResourceException ex){
      return key;
    }
  }
  public String getMessage(String key, CharSequence param){
    String temp = getMessage(key);
    //assuming {0} is not used in the real string,
    //and it is only a placeholder for the first parameter.
    return temp.replace("{0}", param);
  }
  public String getMessage(String key, CharSequence param0, CharSequence param1){
    String temp = getMessage(key);
    return temp.replace("{0}", param0).replace("{1}", param1);
  }
}