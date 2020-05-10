package org.chemvantage;

import java.util.List;

import com.googlecode.objectify.annotation.Entity;

@Entity
public class ProposedQuestion extends Question {
	
	private static final long serialVersionUID = 137L;

	ProposedQuestion() {}
	ProposedQuestion(int t) {
		super(t);
	}
	ProposedQuestion (long topicId,long videoId,String text,String type,int nChoices,List<String> choices,
			double requiredPrecision,int significantFigures,String correctAnswer,String tag,int pointValue,String parameterString,
			String hint,String solution,String authorId,String contributorId,String editorId,String notes) {
		super(topicId,text,type,nChoices,choices,requiredPrecision,significantFigures,correctAnswer,tag,pointValue,parameterString,hint,solution,authorId,contributorId,editorId,notes);
	}
}