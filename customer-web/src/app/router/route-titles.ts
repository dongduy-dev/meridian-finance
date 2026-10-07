const routeTitles: Record<string, string> = {
  '/': 'Home',
  '/products': 'Products',
  '/products/salary-advance': 'Salary Advance',
  '/products/salary-advance/apply': 'Apply for Salary Advance',
  '/products/unsecured-consumer-loan': 'Unsecured Consumer Loan',
  '/products/unsecured-consumer-loan/apply': 'Apply for Unsecured Consumer Loan',
  '/products/collateral-loan': 'Collateral Loan',
  '/products/collateral-loan/apply': 'Apply for Collateral Loan',
  '/applications': 'Applications',
  '/loans': 'Loans',
  '/account': 'Account',
  '/account/profile': 'Profile',
  '/account/identity-verification': 'Identity verification',
  '/account/bank-accounts': 'Bank accounts',
  '/login': 'Login',
  '/register': 'Create Account',
  '/verify-email': 'Confirm Email',
  '/activate-access': 'Activate your access',
  '/verify-email/pending': 'Email Confirmation Required',
  '/forgot-password': 'Forgot Password',
  '/reset-password': 'Reset Password',
}

export function customerRouteTitle(pathname: string, search = '') {
  pathname = pathname.replace(/\/+$/, '') || '/'
  if (routeTitles[pathname]) return routeTitles[pathname]
  const application = /^\/applications\/[^/]+(?:\/(documents|corrections|offer|contract))?$/.exec(pathname)
  if (application) {
    const titles: Record<string, string> = {
      documents: 'Application documents', corrections: 'Requested changes',
      offer: 'Your offer', contract: 'Your contract',
    }
    return application[1] ? titles[application[1]] : 'Application details'
  }
  if (/^\/loans\/[^/]+$/.test(pathname)) {
    return new URLSearchParams(search).get('tab') === 'repayments' ? 'Repayment history' : 'Loan details'
  }
  return 'Page unavailable'
}

