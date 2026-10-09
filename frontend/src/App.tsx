import { BrowserRouter, Route, Routes } from 'react-router-dom'

import ManagerHome from './routes/manager'
import CaregiverHome from './routes/caregiver'
import FamilyHome from './routes/family'
import ElderHome from './routes/elder'
import LandingHome from './routes/landing'
import FamilySignUp from './routes/landing/FamilySignUp'
import ElderSignUp from './routes/landing/ElderSignUp'
import NotFound from './routes/not-found'
import { RequireRole } from './shared/components/RequireRole'

/**
 * One React application serving four kinds of user. Each role owns one folder
 * under routes/, so no two people edit the same file.
 *
 * The landing page (sign-in + pitch) is the entry point and the only sign-in
 * screen. Every role client sits behind RequireRole: without a session the
 * visitor is sent back to the landing page, and a user of another role is sent
 * to their own client, so typing a role URL never shows its screens. The one other public
 * page is family sign-up, since a family member applying for care has no account yet.
 */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LandingHome />} />
        <Route path="/apply" element={<FamilySignUp />} />
        <Route path="/elder/register" element={<ElderSignUp />} />
        <Route path="/manager/*" element={<RequireRole role="MANAGER"><ManagerHome /></RequireRole>} />
        <Route path="/caregiver/*" element={<RequireRole role="CAREGIVER"><CaregiverHome /></RequireRole>} />
        <Route path="/family/*" element={<RequireRole role="FAMILY"><FamilyHome /></RequireRole>} />
        <Route path="/elder/*" element={<RequireRole role="ELDER"><ElderHome /></RequireRole>} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </BrowserRouter>
  )
}
