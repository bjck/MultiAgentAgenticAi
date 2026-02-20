import React, { useState, useEffect } from 'react';
import '../styles/ModelSelector.css';
import { useWebSocket } from '../context/WebSocketProvider';

const ModelSelector = () => {
  const {
    selectedProvider,
    setSelectedProvider,
    selectedModel,
    setSelectedModel,
  } = useWebSocket();

  const [models, setModels] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchModels = async () => {
      setError(null);
      try {
        const providerParam = selectedProvider ? `?provider=${encodeURIComponent(selectedProvider)}` : '';
        const response = await fetch(`/api/models${providerParam}`);
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        const modelIds = Array.isArray(data?.data)
          ? data.data.map((model) => model.id).filter(Boolean)
          : [];
        setModels(modelIds);
        if (modelIds.length > 0) {
          setSelectedModel((prev) => (prev && modelIds.includes(prev) ? prev : modelIds[0]));
        } else {
          setSelectedModel('');
        }
      } catch (e) {
        setError(e.message);
      }
    };

    fetchModels();
  }, [selectedProvider, setSelectedModel]);

  return (
    <div className="model-selector">
      <div className="model-selector-row">
        <label htmlFor="provider-select">Provider:</label>
        <select
          id="provider-select"
          value={selectedProvider}
          onChange={(event) => setSelectedProvider(event.target.value)}
        >
          <option value="GOOGLE">Google</option>
          <option value="OPENAI">OpenAI</option>
        </select>
      </div>
      <div className="model-selector-row">
        <label htmlFor="model-select">Model:</label>
        <select
          id="model-select"
          value={selectedModel}
          onChange={(event) => setSelectedModel(event.target.value)}
          disabled={models.length === 0}
        >
          {models.map((model) => (
            <option key={model} value={model}>
              {model}
            </option>
          ))}
        </select>
      </div>
      {error && <div className="model-selector-error">Error: {error}</div>}
    </div>
  );
};

export default ModelSelector;
