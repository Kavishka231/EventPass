import type { BookingStatus } from '../../types';

export function paymentOutcome(status: BookingStatus) {
  if (status === 'CONFIRMED') return 'Payment completed';
  if (status === 'CANCELLED') return 'Payment refunded';
  if (status === 'PENDING') return 'Payment processing';
  return 'Payment not completed';
}

export function refundOutcome(status: BookingStatus) {
  if (status === 'CANCELLED') return 'Refund completed';
  if (status === 'CONFIRMED') return 'No refund requested';
  return 'Not applicable';
}
