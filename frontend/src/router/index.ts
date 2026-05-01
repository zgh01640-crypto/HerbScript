import { createRouter, createWebHistory } from "vue-router";
import { authService } from "../services/authService";
import DashboardView from "../views/DashboardView.vue";
import LoginView from "../views/LoginView.vue";
import PatientDetailView from "../views/PatientDetailView.vue";
import PatientListView from "../views/PatientListView.vue";
import PrescriptionDetailView from "../views/PrescriptionDetailView.vue";
import PrescriptionEditView from "../views/PrescriptionEditView.vue";
import PrescriptionListView from "../views/PrescriptionListView.vue";
import PrescriptionNewView from "../views/PrescriptionNewView.vue";
import RecognitionWorkbenchView from "../views/RecognitionWorkbenchView.vue";

const routes = [
  {
    path: "/",
    redirect: "/dashboard"
  },
  {
    path: "/login",
    name: "login",
    component: LoginView
  },
  {
    path: "/dashboard",
    name: "dashboard",
    component: DashboardView
  },
  {
    path: "/prescriptions",
    name: "prescriptions",
    component: PrescriptionListView
  },
  {
    path: "/patients",
    name: "patients",
    component: PatientListView
  },
  {
    path: "/patients/:id",
    name: "patient-detail",
    component: PatientDetailView
  },
  {
    path: "/prescriptions/new",
    name: "prescription-new",
    component: PrescriptionNewView
  },
  {
    path: "/prescriptions/:id/edit",
    name: "prescription-edit",
    component: PrescriptionEditView
  },
  {
    path: "/recognition",
    name: "recognition",
    component: RecognitionWorkbenchView
  },
  {
    path: "/prescriptions/:id",
    name: "prescription-detail",
    component: PrescriptionDetailView
  }
] as const;

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to) => {
  if (to.path === "/login") {
    return true;
  }

  if (!authService.isLoggedIn()) {
    return "/login";
  }

  return authService.initializeSession().then((user) => {
    if (!user) {
      return "/login";
    }

    return true;
  });
});

router.beforeEach((to) => {
  if (to.path === "/login" && authService.isLoggedIn()) {
    return "/dashboard";
  }

  return true;
});

export default router;
