import { useEffect, useState } from 'react';
import {
  getFlags,
  getFlagById,
  createFlag,
  updateFlag,
  deleteFlag,
  evaluateFlag,
} from './api/axios';
import './App.css';

const EMPTY_FORM = {
  name: '',
  flagKey: '',
  description: '',
  rolloutPercentage: 0,
  enabled: false,
};

function App() {
  const [flags, setFlags] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');

  // Create / Edit modal
  const [showModal, setShowModal] = useState(false);
  const [modalMode, setModalMode] = useState('create');
  const [editingFlagId, setEditingFlagId] = useState(null);
  const [formData, setFormData] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');

  // Row action states
  const [updatingFlagId, setUpdatingFlagId] = useState(null);
  const [deletingFlagId, setDeletingFlagId] = useState(null);

  // Evaluation states
  const [evaluationData, setEvaluationData] = useState({
    flagKey: '',
    userId: '',
  });
  const [evaluationResult, setEvaluationResult] = useState(null);
  const [evaluating, setEvaluating] = useState(false);
  const [evaluationError, setEvaluationError] = useState('');

  const fetchFlags = async () => {
    try {
      setLoading(true);
      setError('');

      const data = await getFlags();
      setFlags(data);
    } catch (err) {
      console.error('Failed to fetch flags:', err);
      setError(
          err.response?.data?.message ||
          'Failed to load feature flags. Please try again.'
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchFlags().catch(console.error);
  }, []);

  const getErrorMessage = (err, fallback) =>
      err.response?.data?.message || err.response?.data?.detail || fallback;

  // Open create modal
  const openCreateModal = () => {
    setModalMode('create');
    setEditingFlagId(null);
    setFormData({ ...EMPTY_FORM });
    setFormError('');
    setShowModal(true);
  };

  // Open edit modal and fetch the latest flag details
  const openEditModal = async (flag) => {
    setFormError('');
    setActionError('');
    setSaving(true);

    try {
      const latestFlag = await getFlagById(flag.id);

      setModalMode('edit');
      setEditingFlagId(flag.id);
      setFormData({
        name: latestFlag.name,
        flagKey: latestFlag.flagKey,
        description: latestFlag.description || '',
        rolloutPercentage: latestFlag.rolloutPercentage,
        enabled: latestFlag.enabled,
      });
      setShowModal(true);
    } catch (err) {
      console.error('Failed to fetch flag details:', err);
      setActionError(getErrorMessage(err, 'Could not load flag details.'));
    } finally {
      setSaving(false);
    }
  };

  const handleFormChange = (event) => {
    const { name, value, type, checked } = event.target;

    setFormData((previous) => ({
      ...previous,
      [name]: type === 'checkbox' ? checked : value,
    }));
  };

  const closeModal = () => {
    if (saving) return;

    setShowModal(false);
    setFormData({ ...EMPTY_FORM });
    setFormError('');
    setEditingFlagId(null);
  };

  // Create or update a flag
  const handleSubmit = async (event) => {
    event.preventDefault();

    setSaving(true);
    setFormError('');

    const payload = {
      name: formData.name.trim(),
      description: formData.description.trim(),
      rolloutPercentage: Number(formData.rolloutPercentage),
      enabled: modalMode === 'create' ? false : formData.enabled,
    };

    try {
      if (modalMode === 'create') {
        const newFlag = await createFlag({
          ...payload,
          flagKey: formData.flagKey.trim(),
        });

        setFlags((previous) => [...previous, newFlag]);
      } else {
        const updatedFlag = await updateFlag(editingFlagId, payload);

        setFlags((previous) =>
            previous.map((flag) =>
                flag.id === editingFlagId ? updatedFlag : flag
            )
        );
      }

      setShowModal(false);
      setFormData({ ...EMPTY_FORM });
      setEditingFlagId(null);
    } catch (err) {
      console.error('Failed to save feature flag:', err);
      setFormError(
          getErrorMessage(
              err,
              modalMode === 'create'
                  ? 'Failed to create feature flag.'
                  : 'Failed to update feature flag.'
          )
      );
    } finally {
      setSaving(false);
    }
  };

  // Enable or disable a flag
  const handleToggleFlag = async (flag) => {
    setUpdatingFlagId(flag.id);
    setActionError('');

    try {
      const updatedFlag = await updateFlag(flag.id, {
        name: flag.name,
        description: flag.description || '',
        enabled: !flag.enabled,
        rolloutPercentage: flag.rolloutPercentage,
      });

      setFlags((previous) =>
          previous.map((item) => (item.id === flag.id ? updatedFlag : item))
      );
    } catch (err) {
      console.error('Failed to toggle flag:', err);
      setActionError(getErrorMessage(err, `Could not update "${flag.name}".`));
    } finally {
      setUpdatingFlagId(null);
    }
  };

  // Delete a flag
  const handleDeleteFlag = async (flag) => {
    const confirmed = window.confirm(
        `Are you sure you want to delete "${flag.name}"? This action cannot be undone.`
    );

    if (!confirmed) return;

    setDeletingFlagId(flag.id);
    setActionError('');

    try {
      await deleteFlag(flag.id);

      setFlags((previous) => previous.filter((item) => item.id !== flag.id));
    } catch (err) {
      console.error('Failed to delete flag:', err);
      setActionError(getErrorMessage(err, `Could not delete "${flag.name}".`));
    } finally {
      setDeletingFlagId(null);
    }
  };

  // Evaluation form changes
  const handleEvaluationChange = (event) => {
    const { name, value } = event.target;

    setEvaluationData((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  // Evaluate a flag for a particular user
  const handleEvaluateFlag = async (event) => {
    event.preventDefault();

    setEvaluating(true);
    setEvaluationError('');
    setEvaluationResult(null);

    try {
      const result = await evaluateFlag({
        flagKey: evaluationData.flagKey.trim(),
        userId: evaluationData.userId.trim(),
      });

      setEvaluationResult(result);
    } catch (err) {
      console.error('Failed to evaluate flag:', err);
      setEvaluationError(
          getErrorMessage(err, 'Failed to evaluate this feature flag.')
      );
    } finally {
      setEvaluating(false);
    }
  };

  const enabledCount = flags.filter((flag) => flag.enabled).length;
  const disabledCount = flags.length - enabledCount;

  return (
      <div className="app">
        <aside className="sidebar">
          <h2 className="brand">LaunchControl</h2>

          <nav>
            <div className="nav-item active" aria-current="page">
              Feature Flags
            </div>
            <div className="nav-item">Experiments</div>
            <div className="nav-item">Environments</div>
          </nav>

          <div className="sidebar-footer">Feature Management Platform</div>
        </aside>

        <main className="main-content">
          <header className="topbar">
            <span>Development Environment</span>
            <span className="status">
            <i className="status-dot" aria-hidden="true" />
            System Operational
          </span>
          </header>

          <section className="page-content">
            <div className="page-heading">
              <div>
                <h1>Feature Flags</h1>
                <p className="subtitle">
                  Manage and control feature releases across your application.
                </p>
              </div>

              <button className="primary-button" onClick={openCreateModal}>
                + Create Flag
              </button>
            </div>

            {/* Dashboard statistics */}
            <div className="stats">
              <div className="stat-card">
                <p className="stat-label">Total Flags</p>
                <p className="stat-value">{flags.length}</p>
              </div>

              <div className="stat-card">
                <p className="stat-label">Enabled</p>
                <p className="stat-value stat-enabled">{enabledCount}</p>
              </div>

              <div className="stat-card">
                <p className="stat-label">Disabled</p>
                <p className="stat-value">{disabledCount}</p>
              </div>
            </div>

            {/* Feature flags table */}
            <div className="table-card">
              <div className="table-heading">
                <h2>All Feature Flags</h2>
                <p>Flag configuration and rollout status</p>
              </div>

              {actionError && (
                  <div className="form-error action-error">{actionError}</div>
              )}

              {loading ? (
                  <div className="empty-state">Loading feature flags...</div>
              ) : error ? (
                  <div className="empty-state">
                    <h3>Something went wrong</h3>
                    <p>{error}</p>
                    <button className="primary-button" onClick={fetchFlags}>
                      Retry
                    </button>
                  </div>
              ) : flags.length === 0 ? (
                  <div className="empty-state">
                    <h3>No feature flags yet</h3>
                    <p>Create your first flag to get started.</p>
                  </div>
              ) : (
                  <div className="flags-table-wrapper">
                    <table className="flags-table">
                      <thead>
                      <tr>
                        <th>Flag Name</th>
                        <th>Flag Key</th>
                        <th>Status</th>
                        <th>Rollout</th>
                        <th>Actions</th>
                      </tr>
                      </thead>

                      <tbody>
                      {flags.map((flag) => (
                          <tr key={flag.id}>
                            <td>
                              <div className="flag-name">{flag.name}</div>
                              {flag.description && (
                                  <div className="flag-description">
                                    {flag.description}
                                  </div>
                              )}
                            </td>

                            <td>
                              <code>{flag.flagKey}</code>
                            </td>

                            <td>
                          <span
                              className={
                                flag.enabled
                                    ? 'flag-status enabled'
                                    : 'flag-status disabled'
                              }
                          >
                            {flag.enabled ? 'Enabled' : 'Disabled'}
                          </span>
                            </td>

                            <td>
                              <div className="rollout">
                                <div
                                    className="rollout-bar"
                                    role="progressbar"
                                    aria-valuemin={0}
                                    aria-valuemax={100}
                                    aria-valuenow={flag.rolloutPercentage}
                                >
                              <span
                                  style={{ width: `${flag.rolloutPercentage}%` }}
                              />
                                </div>
                                <span className="rollout-value">
                              {flag.rolloutPercentage}%
                            </span>
                              </div>
                            </td>

                            <td>
                              <div className="table-actions">
                                <button
                                    className="secondary-button"
                                    onClick={() => openEditModal(flag)}
                                    disabled={
                                        saving ||
                                        updatingFlagId === flag.id ||
                                        deletingFlagId === flag.id
                                    }
                                >
                                  Edit
                                </button>

                                <button
                                    className={
                                      flag.enabled
                                          ? 'toggle-button disable-button'
                                          : 'toggle-button enable-button'
                                    }
                                    onClick={() => handleToggleFlag(flag)}
                                    disabled={
                                        updatingFlagId === flag.id ||
                                        deletingFlagId === flag.id
                                    }
                                >
                                  {updatingFlagId === flag.id
                                      ? 'Updating...'
                                      : flag.enabled
                                          ? 'Disable'
                                          : 'Enable'}
                                </button>

                                <button
                                    className="delete-button"
                                    onClick={() => handleDeleteFlag(flag)}
                                    disabled={
                                        deletingFlagId === flag.id ||
                                        updatingFlagId === flag.id
                                    }
                                >
                                  {deletingFlagId === flag.id
                                      ? 'Deleting...'
                                      : 'Delete'}
                                </button>
                              </div>
                            </td>
                          </tr>
                      ))}
                      </tbody>
                    </table>
                  </div>
              )}
            </div>

            {/* Feature flag evaluation */}
            <div className="table-card evaluation-card">
              <div className="table-heading">
                <h2>Evaluate Feature Flag</h2>
                <p>Check whether a specific user receives a feature.</p>
              </div>

              <div className="card-body">
                <form onSubmit={handleEvaluateFlag}>
                  <div className="evaluation-form">
                    <div className="form-group">
                      <label htmlFor="evaluationFlagKey">Flag Key</label>
                      <input
                          id="evaluationFlagKey"
                          name="flagKey"
                          type="text"
                          list="flag-key-options"
                          placeholder="new_checkout"
                          value={evaluationData.flagKey}
                          onChange={handleEvaluationChange}
                          required
                      />
                      <datalist id="flag-key-options">
                        {flags.map((flag) => (
                            <option key={flag.id} value={flag.flagKey} />
                        ))}
                      </datalist>
                    </div>

                    <div className="form-group">
                      <label htmlFor="evaluationUserId">User ID</label>
                      <input
                          id="evaluationUserId"
                          name="userId"
                          type="text"
                          placeholder="user_123"
                          value={evaluationData.userId}
                          onChange={handleEvaluationChange}
                          required
                      />
                    </div>

                    <button
                        type="submit"
                        className="primary-button"
                        disabled={evaluating}
                    >
                      {evaluating ? 'Evaluating...' : 'Evaluate'}
                    </button>
                  </div>
                </form>

                {evaluationError && (
                    <div className="form-error">{evaluationError}</div>
                )}

                {evaluationResult && (
                    <div className="evaluation-result">
                      <h3>Evaluation Result</h3>

                      <dl className="result-grid">
                        <dt>Flag</dt>
                        <dd>
                          <code>{evaluationResult.flagKey}</code>
                        </dd>

                        <dt>User</dt>
                        <dd>{evaluationResult.userId}</dd>

                        <dt>Result</dt>
                        <dd>
                      <span
                          className={
                            evaluationResult.enabled
                                ? 'flag-status enabled'
                                : 'flag-status disabled'
                          }
                      >
                        {evaluationResult.enabled ? 'Enabled' : 'Disabled'}
                      </span>
                        </dd>

                        <dt>Reason</dt>
                        <dd>
                          <code>{evaluationResult.reason}</code>
                        </dd>
                      </dl>
                    </div>
                )}
              </div>
            </div>
          </section>
        </main>

        {/* Create / Edit modal */}
        {showModal && (
            <div className="modal-overlay">
              <div
                  className="modal"
                  role="dialog"
                  aria-modal="true"
                  aria-labelledby="modal-title"
              >
                <div className="modal-header">
                  <div>
                    <h2 id="modal-title">
                      {modalMode === 'create'
                          ? 'Create Feature Flag'
                          : 'Edit Feature Flag'}
                    </h2>

                    <p>
                      {modalMode === 'create'
                          ? 'Configure a new feature flag.'
                          : 'Update the feature flag configuration.'}
                    </p>
                  </div>

                  <button
                      type="button"
                      className="close-button"
                      onClick={closeModal}
                      disabled={saving}
                      aria-label="Close"
                  >
                    ×
                  </button>
                </div>

                <form onSubmit={handleSubmit}>
                  <div className="form-group">
                    <label htmlFor="name">Flag Name</label>
                    <input
                        id="name"
                        name="name"
                        type="text"
                        placeholder="New Checkout Experience"
                        value={formData.name}
                        onChange={handleFormChange}
                        maxLength={150}
                        required
                    />
                  </div>

                  {modalMode === 'create' && (
                      <div className="form-group">
                        <label htmlFor="flagKey">Flag Key</label>
                        <input
                            id="flagKey"
                            name="flagKey"
                            type="text"
                            placeholder="new_checkout"
                            value={formData.flagKey}
                            onChange={handleFormChange}
                            maxLength={100}
                            required
                        />
                        <small>
                          Must be unique. Use lowercase letters and underscores.
                        </small>
                      </div>
                  )}

                  {modalMode === 'edit' && (
                      <div className="form-group">
                        <label htmlFor="flagKeyReadonly">Flag Key</label>
                        <input
                            id="flagKeyReadonly"
                            value={formData.flagKey}
                            disabled
                        />
                        <small>The flag key cannot be changed after creation.</small>
                      </div>
                  )}

                  <div className="form-group">
                    <label htmlFor="description">Description</label>
                    <textarea
                        id="description"
                        name="description"
                        placeholder="Describe what this feature flag controls..."
                        value={formData.description}
                        onChange={handleFormChange}
                        maxLength={500}
                        rows={3}
                    />
                  </div>

                  <div className="form-group">
                    <label htmlFor="rolloutPercentage">
                      Rollout Percentage: {formData.rolloutPercentage}%
                    </label>

                    <input
                        id="rolloutPercentage"
                        name="rolloutPercentage"
                        type="range"
                        min="0"
                        max="100"
                        value={formData.rolloutPercentage}
                        onChange={handleFormChange}
                    />
                  </div>

                  {modalMode === 'edit' && (
                      <div className="form-group checkbox-group">
                        <label>
                          <input
                              name="enabled"
                              type="checkbox"
                              checked={formData.enabled}
                              onChange={handleFormChange}
                          />
                          Enable this feature flag
                        </label>
                      </div>
                  )}

                  {modalMode === 'create' && (
                      <p className="modal-note">
                        New feature flags are created in the disabled state.
                      </p>
                  )}

                  {formError && <div className="form-error">{formError}</div>}

                  <div className="modal-actions">
                    <button
                        type="button"
                        className="secondary-button"
                        onClick={closeModal}
                        disabled={saving}
                    >
                      Cancel
                    </button>

                    <button
                        type="submit"
                        className="primary-button"
                        disabled={saving}
                    >
                      {saving
                          ? 'Saving...'
                          : modalMode === 'create'
                              ? 'Create Flag'
                              : 'Save Changes'}
                    </button>
                  </div>
                </form>
              </div>
            </div>
        )}
      </div>
  );
}

export default App;