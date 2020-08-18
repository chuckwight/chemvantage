<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.Arrays,java.util.Date,org.chemvantage.*"%>

<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>ChemVantage Video</title>
</head>

<body>
<%! 
	String url = null;
	String sig = null;
	int segment = 0;
	long videoId = 0L;
	String videoSerialNumber = "";
	int[] breaks = null;
	int start = 0;
	int end = -1;
	String title = null;
%>

<%
	try {
		sig = request.getParameter("sig");
		User user = User.getUser(sig);
		if (user == null)
			throw new Exception();

		try {
			segment = Integer.parseInt(request.getParameter("Segment"));
		} catch (Exception e) {
			segment = 0;
		}

		try {
			videoId = Long.parseLong(request.getParameter("VideoId"));
		} catch (Exception e) {
		}

		long assignmentId = user.getAssignmentId();
		if (assignmentId > 0L) {
			try {
				videoId = ofy().load().type(Assignment.class).id(assignmentId).now().videoId;
			} catch (Exception e) {
			}
		}
		Video v = ofy().load().type(Video.class).id(videoId).now();
		if (v.breaks == null)
			v.breaks = new int[0];

		title = v.title;
		breaks = v.breaks;
		videoSerialNumber = v.serialNumber;

		if (segment > 0)
			start = v.breaks[segment - 1]; // start at the end of the last segment
		if (v.breaks.length > segment)
			end = v.breaks[segment]; // play to this value and stop

	} catch (Exception e) {
		response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
	}
%>

<div id=video_div style='width:560px;height:315px'></div>
<br>
<div id=quiz_div style='width:560px;background-color:white;display:none'></div>
<p>
<script type=text/javascript>

var tag = document.createElement('script'); tag.src='https://www.youtube.com/iframe_api';
var firstScriptTag = document.getElementsByTagName('script')[0]; firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
var player;
var quiz_div = document.getElementById('quiz_div');
var sig = <%= sig %>;
var segment = <%= segment %>;
var breaks = <%= Arrays.toString(breaks) %>;
var videoSerialNumber = '<%= videoSerialNumber %>';
var start = 0;
var end = -1;

function onYouTubeIframeAPIReady() {
  player = new YT.Player('video_div', {
	height: '315',
	width: '560',
	videoId: '<%= videoSerialNumber %>',
	playerVars: {
	  'enablejsapi': 1,
	  'autoplay': 0,
	  'start': <%= start %>,
	  'end': <%= end %>,
	  'modestbranding': 1,
	  'origin': 'https://' + '<%= request.getServerName() %>'
	},
	events: {
		'onReady': onPlayerReady,
        'onStateChange': onPlayerStateChange
    }
  });
}

function onPlayerReady(event) {
	if (segment==0) {
	  start = 0;
	  if (breaks.length==0) end = -1;
	  else {
		  end = breaks[0];
	  }
	  player.loadVideoById({'videoId':videoSerialNumber,'startSeconds':start,'endSeconds':end});
	  ajaxLoadQuiz();
	}
}

function onPlayerStateChange(event) {
	switch (event.data) {
	  case YT.PlayerState.ENDED:
		if (document.exitFullscreen) document.exitFullscreen();
		else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
	    else if (document.mozCancelFullScreen) document.mozCancelFullScreen();
	    else if (document.msExitFullscreen) document.msExitFullscreen();
		video_div.style.display = 'none';
		quiz_div.style.display = '';
		break;
	  case YT.PlayerState.PLAYING:
		video_div.style.display = '';
		quiz_div.style.display = 'none';
		break;
	  default:
    }
}

function ajaxLoadQuiz() {
  var xmlhttp=GetXmlHttpObject();
  quiz_div.innerHTML = "Loading questions...";
  if (xmlhttp==null) {
	    alert ('Sorry, your browser does not support AJAX. To access the video quiz, switch to a supported browser like Chrome or Safari.');
	    return false;
  }
  xmlhttp.onreadystatechange=function() {
	if (xmlhttp.readyState==4) {
	  quiz_div.innerHTML=xmlhttp.responseText;
	}
  }
  xmlhttp.open('GET','/VideoQuiz?VideoId='+'<%= videoId %>'+'&Segment='+segment+'&sig='+sig,true);
  xmlhttp.send(null);
  return true;
}

function ajaxSubmitQuiz() {
  try {
	var xmlhttp=GetXmlHttpObject();
	xmlhttp.onreadystatechange=function() {
	  if (xmlhttp.readyState==4) {
		quiz_div.style.display = start>=0?'none':'';
		quiz_div.innerHTML = xmlhttp.responseText;
	  }
	}
	xmlhttp.open('POST','/VideoQuiz',true);
	xmlhttp.setRequestHeader('Content-Type','application/x-www-form-urlencoded');
	var formData = new FormData(document.getElementById('quizlet'));
	xmlhttp.send(urlencodeFormData(formData));
  } catch (e) {
	  quiz_div.innerHTML = e.message;  
  }
   
  segment++;
  start = breaks[segment-1];
  end = (breaks.length > segment?breaks[segment]:-1);  // play to this value or stop at end
  try {
	if (start>=0) player.loadVideoById({'videoId':videoSerialNumber,'startSeconds':start,'endSeconds':end});
  } catch (e) {}
  
  return false;
}

function urlencodeFormData(fd){
    var params = new URLSearchParams();
    var pair;
    for(pair of fd.entries()){
        typeof pair[1]=='string' && params.append(pair[0], pair[1]);
    }
    return params.toString();
}

function showWorkBox(qid) {}

var star1 = new Image(); star1.src='images/star1.gif';
var star2 = new Image(); star2.src='images/star2.gif';
var set = false;
function showStars(n) {
  if (!set) {
    document.getElementById('vote').innerHTML=(n==0?'(click a star)':''+n+(n>1?' stars':' star'));
    for (i=1;i<6;i++) {document.getElementById(i).src=(i<=n?star2.src:star1.src)}
  }
}
function setStars(n) {
  if (!set) {
    ajaxStars(n);
    set = true;
    document.getElementById('sliderspan').style='display:none';
  }
}

function ajaxSubmit(url,id,note,email) {
  var xmlhttp;
  if (url.length==0) return false;
  xmlhttp=GetXmlHttpObject();
  if (xmlhttp==null) {
    alert ('Sorry, your browser does not support AJAX!');
    return false;
  }
  xmlhttp.onreadystatechange=function() {
    if (xmlhttp.readyState==4) {
      document.getElementById('feedback' + id).innerHTML='<FONT COLOR=RED><b>Thank you. An editor will review your comment.</b></FONT><p>';
    }
  }
  url += '&QuestionId=' + id + '&sig=' + sig + '&Notes=' + note + '&Email=' + email;
  xmlhttp.open('GET',url,true);
  xmlhttp.send(null);
  return false;
}

function ajaxStars(nStars) {
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
                + 'please take a moment to <a href=/Feedback?sig=' + sig + '>tell us why.</a>.';
                break;
      case '2': msg='2 stars - If you are dissatisfied with ChemVantage, '
                + 'please take a moment to <a href=/Feedback?sig=' + sig + '>tell us why.</a>.';
                break;
      case '3': msg='3 stars - Thank you. <a href=/Feedback?sig=' + sig + '>Click here</a> '
                + ' to provide additional feedback.';
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

</script>

<%= Home.footer %>