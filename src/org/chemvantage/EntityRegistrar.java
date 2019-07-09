package org.chemvantage;

import java.util.Date;

import javax.servlet.ServletContextEvent;  
import javax.servlet.ServletContextListener;

import com.googlecode.objectify.ObjectifyService;

public class EntityRegistrar implements ServletContextListener {
	
     public void contextInitialized(ServletContextEvent servletContextEvent) {
        System.out.println("Starting up: " + new Date());
    
        ObjectifyService.init();
        
        ObjectifyService.register(Assignment.class);
        ObjectifyService.register(BLTIConsumer.class);
        ObjectifyService.register(Domain.class);
        ObjectifyService.register(Group.class);
        ObjectifyService.register(HWTransaction.class);
        ObjectifyService.register(Nonce.class);
        ObjectifyService.register(PracticeExamTransaction.class);
        ObjectifyService.register(ProposedQuestion.class);
        ObjectifyService.register(Question.class);
        ObjectifyService.register(QuizTransaction.class);
        ObjectifyService.register(Response.class);
        ObjectifyService.register(Score.class);
        ObjectifyService.register(Subject.class);
        ObjectifyService.register(Text.class);
        ObjectifyService.register(Topic.class);
        ObjectifyService.register(User.class);
        ObjectifyService.register(UserReport.class);
        ObjectifyService.register(Video.class);
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        System.out.println("Shutting down!");
    }
}