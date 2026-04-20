import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ProvisioningStatus } from '../provisioning-status'

// Mock next-intl
vi.mock("next-intl", () => ({
  useTranslations: (ns: string) => {
    const translator = (key: string, params?: Record<string, unknown>) => {
      let result = `${ns}.${key}`;
      if (params) {
        for (const [k, v] of Object.entries(params)) {
          result = result.replace(`{${k}}`, String(v));
        }
      }
      return result;
    };
    translator.has = () => false;
    return translator;
  },
}));

describe('ProvisioningStatus', () => {
  it('should show all steps with correct labels and derived status text', () => {
    const steps = [
      { step: 'S3_BUCKET' as const, status: 'COMPLETED' as const },
      { step: 'KEYCLOAK_REALM' as const, status: 'PENDING' as const },
      { step: 'OPENFGA_STORE' as const, status: 'PENDING' as const },
      { step: 'INITIAL_ADMIN' as const, status: 'PENDING' as const },
    ]
    render(<ProvisioningStatus steps={steps} onRetry={() => {}} isRetrying={false} />)

    expect(screen.getByText('tenants.provisioning.stepLabels.S3_BUCKET')).toBeInTheDocument()
    expect(screen.getByText('tenants.provisioning.stepLabels.KEYCLOAK_REALM')).toBeInTheDocument()
    expect(screen.getByText('tenants.provisioning.stepLabels.OPENFGA_STORE')).toBeInTheDocument()
    expect(screen.getByText('tenants.provisioning.stepLabels.INITIAL_ADMIN')).toBeInTheDocument()

    expect(screen.getByText('tenants.provisioning.statusText.COMPLETED')).toBeInTheDocument()
    // First PENDING step is derived as IN_PROGRESS
    expect(screen.getByText('tenants.provisioning.statusText.IN_PROGRESS')).toBeInTheDocument()
    expect(screen.getAllByText('tenants.provisioning.statusText.PENDING')).toHaveLength(2)
  })

  it('should show progress bar with completed count', () => {
    const steps = [
      { step: 'S3_BUCKET' as const, status: 'COMPLETED' as const },
      { step: 'KEYCLOAK_REALM' as const, status: 'COMPLETED' as const },
      { step: 'OPENFGA_STORE' as const, status: 'PENDING' as const },
      { step: 'INITIAL_ADMIN' as const, status: 'PENDING' as const },
    ]
    render(<ProvisioningStatus steps={steps} onRetry={() => {}} isRetrying={false} />)
    expect(screen.getByText('tenants.provisioning.progress')).toBeInTheDocument()
    // First PENDING after completed should show as IN_PROGRESS
    expect(screen.getByText('tenants.provisioning.statusText.IN_PROGRESS')).toBeInTheDocument()
  })

  it('should show error and retry button for FAILED step without spinner', () => {
    const steps = [{ step: 'S3_BUCKET' as const, status: 'FAILED' as const, error: 'Connection refused' }]
    render(<ProvisioningStatus steps={steps} onRetry={() => {}} isRetrying={false} />)
    expect(screen.getByText('tenants.provisioning.statusText.FAILED')).toBeInTheDocument()
    expect(screen.getByText('Connection refused')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /tenants\.provisioning\.retry/i })).toBeInTheDocument()
  })

  it('should have aria-live region', () => {
    const steps = [{ step: 'S3_BUCKET' as const, status: 'PENDING' as const }]
    const { container } = render(<ProvisioningStatus steps={steps} onRetry={() => {}} isRetrying={false} />)
    expect(container.querySelector('[aria-live="polite"]')).toBeInTheDocument()
  })

  it('should derive first PENDING step as IN_PROGRESS when no failures', () => {
    render(<ProvisioningStatus steps={null} onRetry={() => {}} isRetrying={false} />)
    // First step derived as IN_PROGRESS, rest as PENDING
    expect(screen.getByText('tenants.provisioning.statusText.IN_PROGRESS')).toBeInTheDocument()
    expect(screen.getAllByText('tenants.provisioning.statusText.PENDING')).toHaveLength(3)
    expect(screen.getByText('tenants.provisioning.progress')).toBeInTheDocument()
  })

  it('should show header spinner while provisioning is active', () => {
    const steps = [
      { step: 'S3_BUCKET' as const, status: 'COMPLETED' as const },
      { step: 'KEYCLOAK_REALM' as const, status: 'PENDING' as const },
      { step: 'OPENFGA_STORE' as const, status: 'PENDING' as const },
      { step: 'INITIAL_ADMIN' as const, status: 'PENDING' as const },
    ]
    const { container } = render(<ProvisioningStatus steps={steps} onRetry={() => {}} isRetrying={false} />)
    expect(container.querySelector('[aria-label="tenants.provisioning.spinnerLabel"]')).toBeInTheDocument()
  })

  it('should not show header spinner when all steps completed', () => {
    const steps = [
      { step: 'S3_BUCKET' as const, status: 'COMPLETED' as const },
      { step: 'KEYCLOAK_REALM' as const, status: 'COMPLETED' as const },
      { step: 'OPENFGA_STORE' as const, status: 'COMPLETED' as const },
      { step: 'INITIAL_ADMIN' as const, status: 'COMPLETED' as const },
    ]
    const { container } = render(<ProvisioningStatus steps={steps} onRetry={() => {}} isRetrying={false} />)
    expect(container.querySelector('[aria-label="tenants.provisioning.spinnerLabel"]')).not.toBeInTheDocument()
  })

  it('should not show header spinner when a step has failed', () => {
    const steps = [
      { step: 'S3_BUCKET' as const, status: 'FAILED' as const, error: 'err' },
      { step: 'KEYCLOAK_REALM' as const, status: 'PENDING' as const },
      { step: 'OPENFGA_STORE' as const, status: 'PENDING' as const },
      { step: 'INITIAL_ADMIN' as const, status: 'PENDING' as const },
    ]
    const { container } = render(<ProvisioningStatus steps={steps} onRetry={() => {}} isRetrying={false} />)
    expect(container.querySelector('[aria-label="tenants.provisioning.spinnerLabel"]')).not.toBeInTheDocument()
  })

  it('should not derive PENDING as IN_PROGRESS when a step has failed', () => {
    const steps = [
      { step: 'S3_BUCKET' as const, status: 'FAILED' as const, error: 'err' },
      { step: 'KEYCLOAK_REALM' as const, status: 'PENDING' as const },
      { step: 'OPENFGA_STORE' as const, status: 'PENDING' as const },
      { step: 'INITIAL_ADMIN' as const, status: 'PENDING' as const },
    ]
    render(<ProvisioningStatus steps={steps} onRetry={() => {}} isRetrying={false} />)
    expect(screen.queryByText('tenants.provisioning.statusText.IN_PROGRESS')).not.toBeInTheDocument()
    expect(screen.getAllByText('tenants.provisioning.statusText.PENDING')).toHaveLength(3)
  })
})
