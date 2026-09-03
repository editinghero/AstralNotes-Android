import { initializeApp, getApps, getApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider, signInWithPopup, signOut, onAuthStateChanged, type User } from 'firebase/auth';
import { initializeFirestore, persistentLocalCache, persistentMultipleTabManager } from 'firebase/firestore';

const firebaseConfig = {
  apiKey: "AIzaSyDhQdXCl02D6E1WjKKZz3IdC7UVsqsGMcU",
  authDomain: "astralnotes-android.firebaseapp.com",
  projectId: "astralnotes-android",
  storageBucket: "astralnotes-android.firebasestorage.app",
  messagingSenderId: "173977964592",
  appId: "1:173977964592:web:astralnotes-client"
};

export const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp();

export const auth = getAuth(app);
export const googleProvider = new GoogleAuthProvider();

export const db = initializeFirestore(app, {
  localCache: persistentLocalCache({
    tabManager: persistentMultipleTabManager()
  })
});

export { signInWithPopup, signOut, onAuthStateChanged };
export type { User };
