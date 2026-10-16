import { adminApi } from './adminApi';
import { authApi } from './authApi';
import { favoritesApi } from './favoritesApi';
import { libraryApi } from './libraryApi';
import { reportApi } from './reportApi';
import { searchApi } from './searchApi';

export { ApiError, configureAuth } from './http';

/** Everything the front end asks of the server, grouped by what it is about. */
export const api = {
  ...authApi,
  ...searchApi,
  library: libraryApi,
  reports: reportApi,
  favorites: favoritesApi,
  admin: adminApi,
};
