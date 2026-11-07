
import axios from 'axios';

const api = axios.create({
    baseURL:
        import.meta.env.VITE_API_BASE_URL ||
        'http://localhost:8080/api/v1',
    headers: {
        'Content-Type': 'application/json',
    },
});

// GET: Fetch all feature flags
export const getFlags = async () => {
    const response = await api.get('/flags');
    return response.data;
};

// GET: Fetch one feature flag by ID
export const getFlagById = async (id) => {
    const response = await api.get(`/flags/${id}`);
    return response.data;
};

// POST: Create a feature flag
export const createFlag = async (flagData) => {
    const response = await api.post('/flags', flagData);
    return response.data;
};

// PUT: Update a feature flag
export const updateFlag = async (id, flagData) => {
    const response = await api.put(`/flags/${id}`, flagData);
    return response.data;
};

// DELETE: Delete a feature flag
export const deleteFlag = async (id) => {
    await api.delete(`/flags/${id}`);
};

// POST: Evaluate a feature flag for a user
export const evaluateFlag = async (evaluationData) => {
    const response = await api.post('/flags/evaluate', evaluationData);
    return response.data;
};

export default api;
