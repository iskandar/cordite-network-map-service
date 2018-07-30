import { checkToken } from 'scripts/jwtProcess';
const url = window.location.protocol + "//" + window.location.host;

export async function login(loginData){
  const response = await fetch(`${url}/admin/api/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(loginData)
  });
  let status = await response.status;
  if(status === 200) {
    sessionStorage["corditeAccessToken"] = await response.text();
  }
  else{
    console.log(response);
  }
  return response;
}

export async function checkAuth(){
  let status = 403
  const token = sessionStorage['corditeAccessToken'];
  if(token && checkToken(token)){
    status = 200;
  }
  return status;
}

export async function getNodes() {
  const token = sessionStorage["corditeAccessToken"];
  const response = await fetch(`${url}/admin/api/nodes`,{
    method: 'GET',
    headers: {
      'accept': 'application/json',
      "Authorization": `Bearer ${token}`
    }
  })
  let nodes = await response.json();
  return nodes;
}

export async function getNotaries() {
  const response = await fetch(`${url}/admin/api/notaries`,{
    method: 'GET',
    headers: {
      'accept': 'application/json',
      "Authorization": `Bearer ${sessionStorage["corditeAccessToken"]}`
    }
  })
  let notaries = await response.json();
  return notaries;
}