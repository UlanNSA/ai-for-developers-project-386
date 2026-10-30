import { Navigate, Route, Routes } from 'react-router';
import { BookingPage } from './BookingPage';
import { HomePage } from './HomePage';

export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/book/:meetingTypeId" element={<BookingPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
