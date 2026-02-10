import React, { useState, useEffect } from 'react';
import './App.css';

function App() {
  const [jwt, setJwt] = useState('');
  const [assignmentType, setAssignmentType] = useState('');
  const [htmlContent, setHtmlContent] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const jwtParam = params.get('jwt');
    
    if (!jwtParam) {
      setError('No jwt found. Launch from LMS.');
      return;
    }
    
    setJwt(jwtParam);
    loadAssignment(jwtParam);
  }, []);

  async function loadAssignment(token) {
    try {
      const response = await fetch('/Homework', {
        method: 'GET',
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
        },
      });
      
      if (!response.ok) {
        const text = await response.text();
        console.log('Response status:', response.status);
        console.log('Response text:', text);
        setError(`Server error ${response.status}: ${text.substring(0, 200)}`);
        return;
      }
      
      const data = await response.json();
      if (data.ok && data.html) {
        setHtmlContent(data.html);
        if (data.jwt) setJwt(data.jwt);
        if (data.assignmentType) setAssignmentType(data.assignmentType);
      } else {
        setError(data.error || 'Failed to load homework.');
      }
    } catch (err) {
      console.error('Network error:', err);
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
      if (data.ok && data.html) {
        setHtmlContent(data.html);
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
      <main>
        {error && <div style={{ color: 'red' }}>{error}</div>}
        <div dangerouslySetInnerHTML={{ __html: htmlContent }} />
      </main>
    </div>
  );
}

export default App;
