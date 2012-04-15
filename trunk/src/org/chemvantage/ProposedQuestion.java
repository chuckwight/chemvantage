package org.chemvantage;
/*
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Id;
import javax.persistence.Transient;
*/
import java.util.List;

import com.googlecode.objectify.annotation.Cached;

@Cached
public class ProposedQuestion extends Question {
	
	private static final long serialVersionUID = 137L;

	ProposedQuestion() {}
	ProposedQuestion(int t) {
		super(t);
	}
	ProposedQuestion (long topicId,String text,String type,int nChoices,List<String> choices,
			double requiredPrecision,String correctAnswer,String tag,int pointValue,String parameterString,
			String hint,String solution,String authorId,String contributorId,String editorId,String notes) {
		super(topicId,text,type,nChoices,choices,requiredPrecision,correctAnswer,tag,pointValue,parameterString,hint,solution,authorId,contributorId,editorId,notes);
	}
	
	/*
  	@Id Long id;       // this id is used to store the object in the datastore
	long questionId;   // this id corresponds to the original Question object, if any
	long topicId;
	String assignmentType;
	String text;
	String type;
	int nChoices=0;
	List<String> choices = new ArrayList<String>();
	double requiredPrecision=2;
	String correctAnswer;
	String tag;
	int pointValue=1;
	String parameterString;
	String hint;
	String solution;
	String authorId;
	String contributorId;
	String editorId;
	String notes;
	@Transient int[] parameters = {0,0,0,0};
	boolean requiresParser = false;
	boolean isActive = false;
	
	public static final int MULTIPLE_CHOICE = 1;
	public static final int TRUE_FALSE = 2;
	public static final int SELECT_MULTIPLE = 3;
	public static final int FILL_IN_WORD = 4;
	public static final int NUMERIC = 5;
	

	ProposedQuestion() {}
	
	ProposedQuestion(Question q) {
		this.assignmentType = q.assignmentType;
		this.topicId = q.topicId;
		this.text = q.text;
		this.type = q.type;
		this.nChoices = q.nChoices;
		this.choices = q.choices;
		this.requiredPrecision = q.requiredPrecision;
		this.correctAnswer = q.correctAnswer;
		this.tag = q.tag;
		this.pointValue = q.pointValue;
		this.parameterString = q.parameterString;
		this.hint = q.hint;
		this.solution = q.solution;
		this.authorId = q.authorId;
		this.contributorId = q.contributorId;
		this.editorId = q.editorId;
		this.notes = "";
		this.isActive = false;
	}

	ProposedQuestion(int t) {
		switch (t) {
		case (1): this.type = "MULTIPLE_CHOICE"; break;
		case (2): this.type = "TRUE_FALSE"; break;
		case (3): this.type = "SELECT_MULTIPLE"; break;
		case (4): this.type = "FILL_IN_WORD"; break;
		case (5): this.type = "NUMERIC"; break;
		default:  this.type = null;
		}
		this.correctAnswer = "";
		this.parameterString = "";
		this.requiresParser = false;
		this.pointValue = 1;
		this.requiredPrecision = 2.0;
		this.isActive = false;
	}

	ProposedQuestion (long topicId,String text,String type,int nChoices,List<String> choices,
			double requiredPrecision,String correctAnswer,String tag,int pointValue,String parameterString,
			String hint,String solution,String authorId,String contributorId,String editorId,String notes) {
		this.topicId = topicId;
		this.text = text;
		this.type = type;
		this.nChoices = nChoices;
		this.choices = choices;
		this.requiredPrecision = requiredPrecision;
		this.correctAnswer = correctAnswer;
		this.tag = tag;
		this.pointValue = pointValue;
		this.parameterString = parameterString;
		this.hint = hint;
		this.solution = solution;
		this.authorId = authorId;
		this.contributorId = contributorId;
		this.editorId = editorId;
		this.notes = "";
		this.isActive = false;
	}
*/
}