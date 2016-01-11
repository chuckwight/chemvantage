/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2011 ChemVantage LLC
*   
*    This program is free software: you can redistribute it and/or modify
*   it under the terms of the GNU General Public License as published by
*   the Free Software Foundation, either version 3 of the License, or
*   (at your option) any later version.
*
*   This program is distributed in the hope that it will be useful,
*   but WITHOUT ANY WARRANTY; without even the implied warranty of
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*   GNU General Public License for more details.
*
*   You should have received a copy of the GNU General Public License
*   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.chemvantage;

import java.io.Serializable;

import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

@Cache @Entity
public class Video implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
    	String serialNumber;
    	String title;
    	String orderBy;

    Video() {}
    
    Video(String serialNumber, String title) {
        this.serialNumber = serialNumber;
        this.title = title;
        this.orderBy = "";
    }

    Video(String serialNumber, String title, String orderBy) {
        this.serialNumber = serialNumber;
        this.title = title;
        this.orderBy = orderBy;
    }
}