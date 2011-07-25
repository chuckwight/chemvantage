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

import java.util.Date;

import javax.persistence.Id;

public class ExamTransaction {
    @Id Long id;
    long topicID;
    String userID;
    Date downloaded;
    Date graded;
    int score;
    int possibleScore;
    String IPNumber;

    ExamTransaction() {}
    
    ExamTransaction(long topicID,String userID,Date downloaded,Date graded,int score,int possibleScore,String IPNumber) {
    	this.topicID = topicID;
    	this.userID = userID;
        this.downloaded = downloaded;
        this.graded = graded;
        this.score = score;
        this.possibleScore = possibleScore;
        this.IPNumber = IPNumber;
    }
}