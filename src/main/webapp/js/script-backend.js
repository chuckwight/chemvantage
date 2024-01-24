var star1 = new Image();
var star2 = new Image();
star1.src = '/images/star1.gif';
star2.src = '/images/star2.gif';
var set = false;
function showStars(n) {
  if (!set) {
    document.getElementById('vote').innerHTML = (n == 0 ? '(click a star)' : '' + n + (n > 1 ? ' stars' : ' star'));
    for (i = 1; i < 6; i++) document.getElementById(i).src = (i <= n ? star2.src : star1.src);
  }
}
function toggleTimers() {
	var timer0 = document.getElementById('timer0');
	var timer1 = document.getElementById('timer1');
	var ctrl0 = document.getElementById('ctrl0');
	var ctrl1 = document.getElementById('ctrl1');
	if (timer0.style.display=='') {
		timer0.style.display='none';timer1.style.display='none';
		ctrl0.innerHTML='<a href=javascript:toggleTimers()>show timers</a><p>';
		ctrl1.innerHTML='<a href=javascript:toggleTimers()>show timers</a><p>';
	} else {
		timer0.style.display='';
		timer1.style.display='';
		ctrl0.innerHTML='<a href=javascript:toggleTimers()>hide timers</a><p>';
		ctrl1.innerHTML='<a href=javascript:toggleTimers()>hide timers</a><p>';
	}
}

function setFeedbackStars(n) {
	set = (n > 0 ? true : false);
	if (set) {
		document.FeedbackForm.Stars.value = n;
		document.getElementById('sliderspan').style='display:none';
	}
}
function setStars(n,sig) {
  if (!set) ajaxStars(n,sig);
  set = true;
  document.getElementById('sliderspan').style='display:none';
}

var endMillis;
function countdown() {
	var seconds;
    var minutes;
    var oddSeconds;
    var nowMillis = new Date().getTime();
    var seconds=Math.round((endMillis-nowMillis)/1000);
	var minutes = seconds<0?Math.ceil(seconds/60.):Math.floor(seconds/60.);
	var oddSeconds = seconds%60;
	for (i=0;i<2;i++) try {  // change the display of timer0 and/or timer1 in the parent page
		    document.getElementById('timer'+i).innerHTML='Time remaining: ' + (minutes==0?'':minutes + (minutes==1?' minute ':' minutes ')) + oddSeconds + ' seconds.';}
		catch(Exception){}
    if (seconds <= 0) try { timesUp() } catch (Exception) {} // run a custom script function in the parent page
	else setTimeout('countdown()',1000);
}
function startTimers(m) {
	endMillis = m;
	countdown();
}
function confirmSubmission() {
	var elements = document.getElementById('quizForm').elements;
	var nAnswers;
	var i;
	var checkboxes;
	var lastCheckboxIndex;
	nAnswers = 0;
	for (i=0;i<elements.length;i++) {
		if (isNaN(elements[i].name)) continue;
		if (elements[i].type=='text' && elements[i].value.length>0) nAnswers++;
		else if (elements[i].type=='radio' && elements[i].checked) nAnswers++;
		else if (elements[i].type=='checkbox') {
			checkboxes = document.getElementsByName(elements[i].name);
			lastCheckboxIndex = i + checkboxes.length - 1;
			for (j=0;j<checkboxes.length;j++) if (checkboxes[j].checked==true) {
				nAnswers++;
				i = lastCheckboxIndex;
				break;
			}    
		}  
	}  
	if (nAnswers<10) return confirm('Submit this quiz for scoring now? ' + (10-nAnswers) + ' answers may be left blank.');
	else return true;
}

function ajaxSubmit(url,id,params,studentAnswer,note,email) {
  var xmlhttp;
  if (url.length==0) return false;
  xmlhttp=GetXmlHttpObject();
  if (xmlhttp==null) {
    alert ('Sorry, your browser does not support AJAX!');
    return false;
  }
  xmlhttp.onreadystatechange=function() {
    if (xmlhttp.readyState==4) {
      document.getElementById('feedback' + id).innerHTML=
      '<FONT COLOR=#EE0000><b>Thank you. An editor will review your comment.</b></FONT><p>';
    }
  }
  url += '&QuestionId=' + id + '&Params=' + params + '&Notes=' + note + '&Email=' + email + '&StudentAnswer=' + studentAnswer;
  xmlhttp.open('GET',url,true);
  xmlhttp.send(null);
  return false;
}
function synchronizeScore(forUserId,sig,path) {
  let xmlhttp=GetXmlHttpObject();
  let url = path + '?UserRequest=SynchronizeScore&sig=' + sig + '&ForUserId=' + forUserId;
  if (xmlhttp==null) {
    alert ('Sorry, your browser does not support AJAX!');
    return false;
  }
  xmlhttp.onreadystatechange=function() {
    if (xmlhttp.readyState==4) {
      if (xmlhttp.responseText.includes('OK')) {
        document.getElementById('cell'+forUserId).innerHTML='OK. Check grade book settings.';
        setTimeout(() => {location.reload();}, 500);
      } else {
        document.getElementById('cell'+forUserId).innerHTML=xmlhttp.responseText;
      }
    }
  }
  xmlhttp.open('GET',url,true);
  xmlhttp.send(null);
  return false;
}
function ajaxStars(nStars,sig) {
  var xmlhttp;
  if (nStars==0) return false;
  xmlhttp=GetXmlHttpObject();
  if (xmlhttp==null) {
    alert ('Sorry, your browser does not support AJAX!');
    return false;
  }
  xmlhttp.onreadystatechange=function() {
    var msg;
    switch (nStars) {
      case '1': msg='1 star - If you are dissatisfied with ChemVantage, '
                + 'please take a moment to <a href=/Feedback?sig=' + sig + '>tell us why</a>.';
                break;
      case '2': msg='2 stars - If you are dissatisfied with ChemVantage, '
                + 'please take a moment to <a href=/Feedback?sig=' + sig + '>tell us why</a>.';
                break;
      case '3': msg='3 stars - Thank you. <a href=/Feedback?sig=' + sig + '>Click here</a> '
                + 'to provide additional feedback.';
                break;
      case '4': msg='4 stars - Thank you';
                break;
      case '5': msg='5 stars - Thank you!';
                break;
      default: msg='You clicked ' + nStars + ' stars.';
    }
    if (xmlhttp.readyState==4) {
      document.getElementById('vote').innerHTML=msg;
    }
  }
  xmlhttp.open('GET','Feedback?UserRequest=AjaxRating&NStars='+nStars,true);
  xmlhttp.send(null);
  return false;
}
function GetXmlHttpObject() {
  if (window.XMLHttpRequest) { // code for IE7+, Firefox, Chrome, Opera, Safari
    return new XMLHttpRequest();
  }
  if (window.ActiveXObject) { // code for IE6, IE5
    return new ActiveXObject('Microsoft.XMLHTTP');
  }
  return null;
}

function waitforSync() {
	let b = document.getElementById('syncAll');
	b.disabled = true;
	b.value = 'Please wait while we recalculate all scores.';
}
function waitForRetryScore() {
 let b = document.getElementById('RetryButton');
 b.disabled = true;
 b.value = 'Please wait a moment while we score your response.';
}

function waitForScore(qid) {
 let b = document.getElementById('sub' + qid);
 b.disabled = true;
 b.value = 'Please wait a moment while we score your response.';
}

function synchTimer(sig) {
  var xmlhttp=new XMLHttpRequest();
  if (xmlhttp==null) {
    alert ('Sorry, your browser does not support AJAX!');
    return false;
  }
  xmlhttp.onreadystatechange=function() {
    if (xmlhttp.readyState==4) {
     const serverNowMillis = xmlhttp.responseText.trim();  // server returned new Date().getTime()
     endMillis += Date.now() - serverNowMillis;          // corrects for fast or slow browser clock
    }
  }
  var url = 'Poll?UserRequest=Synch&sig=' + sig;
  timer0.innerHTML = 'synchronizing clocks...';
  xmlhttp.open('GET',url,true);
  xmlhttp.send(null);
  return false;
}
				
