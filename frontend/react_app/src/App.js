import React, { useState, useEffect } from 'react';
import './App.css';

function App() {
  const [jwt, setJwt] = useState('');
  const [assignmentType, setAssignmentType] = useState('Homework');
  const [htmlContent, setHtmlContent] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const jwtParam = params.get('jwt');
    if (jwtParam) {
      setJwt(jwtParam);
      loadHomework(jwtParam);
    } else {
      setError('No jwt found. Launch from LMS.');
    }
  }, []);

  async function loadHomework(sigOrJwt) {
    try {
      const response = await fetch('/Homework', {
        method: 'GET',
        headers: {
          Accept: 'application/json',
          Authorization: sigOrJwt ? `Bearer ${sigOrJwt}` : '',
        },
      });
      const data = await response.json();
      if (data.ok) {
        setHtmlContent(data.html || '');
        setAssignmentType(data.assignmentType || 'Homework');
        if (data.jwt) setJwt(data.jwt);
      } else {
        setError(data.error || 'Failed to load homework.');
      }
    } catch (err) {
      setError('Network error: ' + err.message);
    }
  }

  async function apiCall(endpoint, params = {}) {
    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          Authorization: jwt ? `Bearer ${jwt}` : '',
        },
        body: JSON.stringify(params),
      });
      const data = await response.json();
      if (data.ok) {
        setHtmlContent(data.html || '');
        setAssignmentType(data.assignmentType || assignmentType);
        if (data.jwt) setJwt(data.jwt);
      } else {
        setError(data.error || 'API call failed');
      }
    } catch (err) {
      setError('API call error: ' + err.message);
    }
  }

  return (
    <div className="App">
      <header className="App-header">
        <h1>ChemVantage {assignmentType}</h1>
      </header>
      <main>
        {error && <div style={{ color: 'red' }}>{error}</div>}
        <div dangerouslySetInnerHTML={{ __html: htmlContent }} />
      </main>
    </div>
  );
}

export default App;
