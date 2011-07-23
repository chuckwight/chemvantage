package org.chemvantage;

import java.io.Serializable;

import javax.persistence.Id;

import com.googlecode.objectify.annotation.Cached;

@Cached
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