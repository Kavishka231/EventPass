import { RouterProvider } from 'react-router-dom';

import { SessionProvider } from '../features/session';
import { router } from '../routes/router';

export function App() {
  return (
    <SessionProvider>
      <RouterProvider router={router} />
    </SessionProvider>
  );
}
