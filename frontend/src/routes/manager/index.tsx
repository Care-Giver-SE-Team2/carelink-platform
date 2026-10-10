import { Route, Routes, useParams } from 'react-router-dom'

import Today from './pages/Today'
import Roster from './pages/Roster'
import Absences from './pages/Absences'
import AbsenceDetail from './pages/absences/AbsenceDetail'
import Exceptions from './pages/Exceptions'
import ExceptionDetail from './pages/exceptions/ExceptionDetail'
import Elders from './pages/Elders'
import CarePlan from './pages/CarePlan'
import Caregivers from './pages/Caregivers'
import Certifications from './pages/Certifications'
import Reports from './pages/Reports'
import ReportDetail from './pages/reports/ReportDetail'
import Quality from './pages/Quality'
import ExtraServices from './pages/ExtraServices'

/**
 * Keys CarePlan by elderId so navigating between two elders' plans (e.g. via
 * the breadcrumb back to 5d and into a different row) remounts fresh state
 * instead of reusing the previous elder's tree/draft state.
 */
function CarePlanRoute() {
  const { elderId } = useParams()
  return <CarePlan key={elderId} />
}

/**
 * Manager console (主管台) — routes for the eight sidebar screens.
 *
 * Each page owns its content and renders itself inside ManagerShell; this
 * file only maps sidebar paths to pages. See README.md in this folder for the
 * use cases to cover and the layout notes.
 */
export default function ManagerHome() {
  return (
    <Routes>
      <Route index element={<Today />} />
      <Route path="roster" element={<Roster />} />
      <Route path="absences" element={<Absences />} />
      <Route path="absences/:id" element={<AbsenceDetail />} />
      <Route path="exceptions" element={<Exceptions />} />
      <Route path="exceptions/:id" element={<ExceptionDetail />} />
      <Route path="elders" element={<Elders />} />
      <Route path="elders/:elderId" element={<CarePlanRoute />} />
      <Route path="caregivers" element={<Caregivers />} />
      <Route path="extra-services" element={<ExtraServices />} />
      <Route path="certifications" element={<Certifications />} />
      <Route path="reports" element={<Reports />} />
      <Route path="reports/:id" element={<ReportDetail />} />
      <Route path="quality" element={<Quality />} />
    </Routes>
  )
}
