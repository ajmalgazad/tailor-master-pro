import type { FirebaseOptions } from 'firebase/app'

/** The shop's Firebase web config (Project settings → Your apps → Web). Not secret: access is protected by Firestore rules + login. */
export const firebaseConfig: FirebaseOptions | null = {
  apiKey: 'AIzaSyDJDBJcyGXItD4s49IgQ3RYJgbgKE_V9gc',
  authDomain: 'tailor-master-pro-a2bfd.firebaseapp.com',
  projectId: 'tailor-master-pro-a2bfd',
  storageBucket: 'tailor-master-pro-a2bfd.firebasestorage.app',
  messagingSenderId: '73575396199',
  appId: '1:73575396199:web:589b78fed4c9625fb0b5c2',
}
