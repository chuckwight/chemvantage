/*  PracticeZone - A Java web application for online learning
    Copyright (C) 2009 PracticeZone.org

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.chemvantage;

class CharHider {

  public String getServletInfo() {
    return "This Eledge servlet module is used to replace single quotation mark characters with "
    + "the string literal equivalent (for entering strings into the database) or with the HTML "
    + "special character equivalent (for including single quotes in web form elements).";  
  }
  
  static String quot2html(String oldString) {
    if (oldString == null) return "";
  // recursive method replaces single quotes with &#39; for HTML pages
    int i = oldString.indexOf('\'',0);
    return i<0?oldString:quot2html(new StringBuffer(oldString).replace(i,i+1,"&#39;").toString(),i);
  }

  static String quot2html(String oldString,int fromIndex) {
  // recursive method replaces single quotes with &#39; for HTML pages
    int i = oldString.indexOf('\'',fromIndex);
    return i<0?oldString:quot2html(new StringBuffer(oldString).replace(i,i+1,"&#39;").toString(),i);
  }

  static String amp2html(String oldString) {
    if (oldString == null) return "";
  // recursive method replaces ampersands with &amp; for preloading Greek/special characters in text fields in HTML forms
    int i = oldString.indexOf('&',0);
//    return i<0?oldString:new StringBuffer(oldString).replace(i,i+1,"&amp;").toString();
    return i<0?oldString:amp2html(new StringBuffer(oldString).replace(i,i+1,"&amp;").toString(),i+1);
  }

  static String amp2html(String oldString,int fromIndex) {
  // recursive method replaces ampersands with &amp; for preloading Greek/special characters in text fields in HTML forms
    int i = oldString.indexOf('&',fromIndex);
    return i<0?oldString:amp2html(new StringBuffer(oldString).replace(i,i+1,"&amp;").toString(),i+1);
  }

  static String quot2literal(String oldString) {
    if (oldString == null) return "";
  // recursive method inserts backslashes before all apostrophes
    int i = oldString.indexOf('\'',0);
    return i<0?oldString:quot2literal(new StringBuffer(oldString).insert(i,'\\').toString(),i+2);
  }

  static String quot2literal(String oldString, int fromIndex) {
  // recursive method inserts backslashes before all apostrophes
    int i = oldString.indexOf('\'',fromIndex);
    return i<0?oldString:quot2literal(new StringBuffer(oldString).insert(i,'\\').toString(),i+2);
  }
}
