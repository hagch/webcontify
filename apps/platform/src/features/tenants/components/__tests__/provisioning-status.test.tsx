import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ProvisioningStatus } from '../provisioning-status'

describe('ProvisioningStatus', () => {
  it('should show all steps with correct labels and status text', () => {
    const steps = [
      { step: 'S3_BUCKET' as const, status: 'COMPLETED' as const },
      { step: 'KEYCLOAK_REALM' as const, status: 'IN_PROGRESS' as const },
      { step: 'OPENFGA_STORE' as const, status: 'PENDING' as const },
      { step: 'INITIAL_ADMIN' as const, status: 'PENDING' as const },
    ]
    render(<ProvisioningStatus steps={steps} onRetry={() => {}} isRetrying={false} />)

    expect(screen.getByText('Speicher')).toBeInTheDocument()
    expect(screen.getByText('Authentifizierung')).toBeInTheDocument()
    expect(screen.getByText('Berechtigungssystem')).toBeInTheDocument()
    expect(screen.getByText('Initialer Admin')).toBeInTheDocument()

    expect(screen.getByText('Abgeschlossen')).toBeInTheDocument()
    expect(screen.getByText('Wird eingerichtet...')).toBeInTheDocument()
    expect(screen.getAllByText('Ausstehend')).toHaveLength(2)
  })

  it('should show progress bar with completed count', () => {
    const steps = [
      { step: 'S3_BUCKET' as const, status: 'COMPLETED' as const },
      { step: 'KEYCLOAK_REALM' as const, status: 'COMPLETED' as const },
      { step: 'OPENFGA_STORE' as const, status: 'IN_PROGRESS' as const },
      { step: 'INITIAL_ADMIN' as const, status: 'PENDING' as const },
    ]
    render(<ProvisioningStatus steps={steps} onRetry={() => {}} isRetrying={false} />)
    expect(screen.getByText('2 von 4 Schritten abgeschlossen')).toBeInTheDocument()
  })

  it('should show error and retry button for FAILED step', () => {
    const steps = [{ step: 'S3_BUCKET' as const, status: 'FAILED' as const, error: 'Connection refused' }]
    render(<ProvisioningStatus steps={steps} onRetry={() => {}} isRetrying={false} />)
    expect(screen.getByText('Fehlgeschlagen')).toBeInTheDocument()
    expect(screen.getByText('Connection refused')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /erneut versuchen/i })).toBeInTheDocument()
  })

  it('should have aria-live region', () => {
    const steps = [{ step: 'S3_BUCKET' as const, status: 'PENDING' as const }]
    const { container } = render(<ProvisioningStatus steps={steps} onRetry={() => {}} isRetrying={false} />)
    expect(container.querySelector('[aria-live="polite"]')).toBeInTheDocument()
  })

  it('should show all steps as PENDING when steps is null', () => {
    render(<ProvisioningStatus steps={null} onRetry={() => {}} isRetrying={false} />)
    expect(screen.getAllByText('Ausstehend')).toHaveLength(4)
    expect(screen.getByText('0 von 4 Schritten abgeschlossen')).toBeInTheDocument()
  })
})
