import { RouterProvider } from 'react-router-dom';

import { SessionProvider } from '../features/session';
import { router } from '../routes/router';
import { ServerStateProvider } from '../services/query';

export function App() {
  return (
    <ServerStateProvider>
      <SessionProvider>
        <RouterProvider router={router} />
      </SessionProvider>
    </ServerStateProvider>
  );
}
