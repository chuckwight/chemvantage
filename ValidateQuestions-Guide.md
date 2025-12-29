# ValidateQuestions Servlet - User Guide

## Overview

The `ValidateQuestions` servlet is a new admin tool that validates question items in the ChemVantage datastore by sending them to ChatGPT in batches of 100. It automatically:

1. **Validates questions** - Sends the question text and correct answer to ChatGPT for validation
2. **Marks valid questions** - Sets `checkedByAI = true` for questions that pass validation
3. **Alerts admins** - Sends email notifications to `admin@chemvantage.org` for invalid questions
4. **Processes in batches** - Handles questions in batches of 100 using Google Cloud Tasks for efficient processing

## Access

The servlet is accessible at: **`/ValidateQuestions`**

**Note:** Access is restricted to ChemVantage admin users by the `login: admin` handler in `app.yaml`.

## Features

### 1. Validation Process

Each question is validated using the OpenAI API (ChatGPT) with the following evaluation criteria:

- **Question clarity** - Is the question text clear and unambiguous?
- **Answer accuracy** - Is the provided correct answer accurate?
- **Overall quality** - Does the question meet chemistry education standards?

### 2. Batch Processing

- Questions are processed in batches of **100** to optimize API usage
- Each batch is queued as a separate Google Cloud Task
- Tasks are delayed to avoid overwhelming the API and respect rate limits
- Processing can take several minutes for large question banks

### 3. Email Notifications

Invalid questions trigger automated emails containing:

- The question ID for easy identification
- The full question text and correct answer
- A direct link to the Edit page for that question
- A request for manual review and correction

### 4. Status Tracking

The interface displays:

- Total number of questions in the database
- Number of questions already checked by AI
- Number of questions pending validation
- Overall validation progress (percentage)

## Usage Instructions

### Starting Validation

1. Navigate to `/ValidateQuestions` (admin access required)
2. Review the current validation status
3. Click **"Start Batch Validation"** button
4. The first batch will be queued to process in approximately 5 seconds

### Monitoring Progress

- Refresh the page to see updated status
- Check email for notifications about invalid questions
- Each batch response shows:
  - Number of questions processed
  - Number of valid questions
  - Number of invalid questions (alerts sent)
  - Time until the next batch processes

### Reviewing Invalid Questions

When you receive an email about an invalid question:

1. Click the provided link or navigate to the Edit page
2. Review the question text and correct answer
3. Determine if the question needs revision or if ChatGPT's assessment was incorrect
4. Make any necessary corrections
5. Note: The `checkedByAI` flag will already be set to `true`; you may want to reset it if significant changes are made

## Implementation Details

### Key Methods

#### `validateQuestionBatch(int offset)`
- Loads a batch of unchecked questions from the datastore
- Validates each question using ChatGPT
- Updates the `checkedByAI` flag for all processed questions
- Queues the next batch if more questions remain

#### `validateQuestionWithChatGPT(Question q)`
- Sends the question to OpenAI's ChatGPT API
- Parses the response to determine if the question is valid
- Returns `true` if valid, `false` if invalid
- Throws exceptions for API errors

#### `sendInvalidQuestionEmail(Question q)`
- Composes a detailed email with question information
- Includes a direct link to edit the question
- Sends to `admin@chemvantage.org`
- Safely handles email send failures

### Datastore Interaction

The servlet queries questions using:

```java
// Find unchecked questions
List<Question> questions = ofy().load().type(Question.class)
    .filter("checkedByAI", false)
    .order("id")
    .offset(offset)
    .limit(100)
    .list();

// Update checked questions
ofy().save().entities(questionsToUpdate).now();
```

### OpenAI API Integration

The validation uses the OpenAI Chat Completions API with:

- **Model:** Uses the configured GPT model from `Subject.getGPTModel()`
- **Temperature:** 0.7 for balanced creativity and consistency
- **System prompt:** Instructs ChatGPT to evaluate as a chemistry educator
- **Expected response:** "VALID" or "INVALID"

## Configuration Requirements

### Required Settings

1. **OpenAI API Key** - Must be configured in the Subject entity (via Admin console)
   - Used for: `Subject.getOpenAIKey()`
   
2. **SendGrid API Key** - Must be configured for email notifications
   - Used for: `Utilities.sendEmail()`

3. **Server URL** - Must be configured in the Subject entity
   - Used for: Edit links in email notifications

4. **Google Cloud Tasks** - Must be enabled for batch processing
   - Default queue configuration is used

### App.yaml Configuration

Ensure the servlet endpoint is configured in `app.yaml` with admin restriction:

```yaml
handlers:
- url: /ValidateQuestions
  login: admin
  script: auto
```

## Performance Considerations

- **API Rate Limits:** Each question makes one API call to OpenAI. Be aware of rate limits.
- **Batch Delays:** Batches are delayed (5 min + 1 min per batch) to manage API load
- **Database Operations:** Uses batch saves for efficiency
- **Email Volume:** Invalid questions generate one email each

## Error Handling

The servlet handles various error scenarios:

- **API Failures:** Logs individual question failures without stopping the batch
- **Email Send Failures:** Logs errors without blocking question updates
- **Missing Configuration:** Returns friendly error messages
- **Authentication Failures:** Returns 403 Forbidden with security logging

## Troubleshooting

### No questions being validated?
- Verify admin access to `/ValidateQuestions`
- Check that `checkedByAI` field exists on Question entity
- Ensure OpenAI API key is configured

### Emails not being sent?
- Check SendGrid API key configuration
- Verify `admin@chemvantage.org` is valid
- Check SendGrid logs for delivery issues

### API errors?
- Verify OpenAI API key is valid and has sufficient credits
- Check rate limit status
- Review API response details in browser console

### Questions marked as checked but not validated?
- This can happen if validation process encounters errors
- Review the batch response details for error messages
- You may need to reset `checkedByAI = false` manually and retry

## Future Enhancements

Potential improvements to consider:

1. **Configurable batch size** - Allow admins to adjust from 100
2. **Detailed validation reports** - Generate comprehensive validation statistics
3. **Selective validation** - Validate only questions from specific topics
4. **Custom validation rules** - Add question-specific validation criteria
5. **Validation history** - Track which version of ChatGPT validated each question
6. **API cost tracking** - Monitor and report API costs
7. **Manual override** - Allow admins to mark questions as valid without ChatGPT

## Support

For issues or questions:
1. Check the ChemVantage admin tools
2. Review OpenAI API documentation
3. Contact the ChemVantage development team
