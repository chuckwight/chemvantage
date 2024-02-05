package org.chemvantage;

import com.googlecode.objectify.ObjectifyService;

import jakarta.servlet.annotation.WebFilter;

@WebFilter(urlPatterns = {"/*"})
public class ObjectifyWebFilter extends ObjectifyService.Filter {}