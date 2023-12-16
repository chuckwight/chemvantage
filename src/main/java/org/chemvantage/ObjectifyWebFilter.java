package org.chemvantage;

import javax.servlet.annotation.WebFilter;

import com.googlecode.objectify.ObjectifyFilter;

@SuppressWarnings("deprecation")
@WebFilter(urlPatterns = "/*")
public class ObjectifyWebFilter extends ObjectifyFilter {}