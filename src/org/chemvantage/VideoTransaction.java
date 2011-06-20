package org.chemvantage;

import java.util.Date;

import javax.persistence.Id;

import com.googlecode.objectify.annotation.Cached;

@Cached
public class VideoTransaction {
    @Id Long id;
    String userId;
    String serialNumber;
    String title;
    Date viewed;

    VideoTransaction() {}
    
    VideoTransaction(String userId, String serialNumber, String title, Date viewed) {
    	this.userId = userId;
        this.serialNumber = serialNumber;
        this.title = title;
        this.viewed = viewed;
    }
}